param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8082,

    [ValidateSet('process', 'docker')]
    [string]$SandboxType = $(if ($env:OJ_SANDBOX_TYPE) { $env:OJ_SANDBOX_TYPE } else { 'process' }),

    [string]$TomcatHome = $env:CATALINA_HOME,
    [string]$MavenHome = $env:MAVEN_HOME
)

$ErrorActionPreference = 'Stop'
$runtime = Join-Path $PSScriptRoot '.tomcat-local'

function Get-ApacheTool {
    param([string]$Name, [string]$ReleasePath, [string]$RequiredFile)

    $tools = Join-Path $runtime 'tools'
    $homeDir = Join-Path $tools $Name
    $marker = Join-Path $homeDir '.installed'
    $executable = Join-Path $homeDir $RequiredFile
    if ((Test-Path -LiteralPath $marker -PathType Leaf) -and (Test-Path -LiteralPath $executable -PathType Leaf)) {
        return $homeDir
    }

    New-Item -ItemType Directory -Path $tools -Force | Out-Null
    $zipPath = Join-Path $tools ($Name + '.zip')
    $checksumPath = $zipPath + '.sha512'
    $sources = 'https://dlcdn.apache.org', 'https://archive.apache.org/dist'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    foreach ($source in $sources) {
        $url = "$source/$ReleasePath"
        try {
            Write-Host "Downloading $Name from Apache..."
            Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing -TimeoutSec 120
            Invoke-WebRequest -Uri "$url.sha512" -OutFile $checksumPath -UseBasicParsing -TimeoutSec 30
            break
        } catch {
            if ($source -eq $sources[-1]) { throw }
        }
    }

    $checksum = [regex]::Match((Get-Content -LiteralPath $checksumPath -Raw), '(?i)\b[0-9a-f]{128}\b').Value
    if ((Get-FileHash -LiteralPath $zipPath -Algorithm SHA512).Hash -ne $checksum) {
        throw "SHA-512 verification failed for $Name."
    }
    Expand-Archive -LiteralPath $zipPath -DestinationPath $tools -Force
    if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) {
        throw "$Name did not contain $RequiredFile."
    }
    New-Item -ItemType File -Path $marker -Force | Out-Null
    Remove-Item -LiteralPath $zipPath, $checksumPath
    return $homeDir
}

function Find-Jdk {
    if ($env:JAVA_HOME) {
        return (Resolve-Path -LiteralPath $env:JAVA_HOME).Path
    }

    $compiler = Get-Command javac.exe -CommandType Application -ErrorAction SilentlyContinue
    if (-not $compiler) {
        throw 'Install JDK 17 or later and set JAVA_HOME.'
    }
    # Oracle's javapath shim may not be inside the JDK installation.
    $ErrorActionPreference = 'Continue'
    $javaInfo = & $compiler.Source -J-XshowSettings:properties -version 2>&1
    $property = [regex]::Match(($javaInfo -join [Environment]::NewLine), '(?m)^\s*java\.home\s*=\s*(.+)$')
    if (-not $property.Success) {
        throw 'Set JAVA_HOME to the JDK installation directory.'
    }
    return $property.Groups[1].Value.Trim()
}

function Find-Maven {
    if ($MavenHome) {
        return (Join-Path $MavenHome 'bin\mvn.cmd')
    }
    $command = Get-Command mvn.cmd -CommandType Application -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $homeDir = Get-ApacheTool 'apache-maven-3.9.16' 'maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip' 'bin\mvn.cmd'
    return (Join-Path $homeDir 'bin\mvn.cmd')
}

New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$lockPath = Join-Path $runtime 'run.lock'
try {
    $instance = [IO.File]::Open($lockPath, 'OpenOrCreate', 'ReadWrite', 'None')
} catch [IO.IOException] {
    throw "Could not lock $lockPath. Close any running Mini OJ launcher and retry."
}

Push-Location -LiteralPath $PSScriptRoot
try {
    $env:JAVA_HOME = Find-Jdk
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
    if (-not ((Test-Path -LiteralPath $java -PathType Leaf) -and (Test-Path -LiteralPath $javac -PathType Leaf))) {
        throw 'JAVA_HOME must point to a JDK, version 17 or later.'
    }
    $compilerVersion = & $javac -version
    if ($LASTEXITCODE -ne 0 -or $compilerVersion -notmatch '^javac (\d+)' -or [int]$Matches[1] -lt 17) {
        throw 'Mini OJ requires JDK 17 or later.'
    }

    $maven = Find-Maven
    & $maven -B -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven could not build the WAR (exit code $LASTEXITCODE)."
    }

    if (-not $TomcatHome) {
        $TomcatHome = Get-ApacheTool 'apache-tomcat-10.1.62' 'tomcat/tomcat-10/v10.1.62/bin/apache-tomcat-10.1.62.zip' 'bin\bootstrap.jar'
    }
    $TomcatHome = (Resolve-Path -LiteralPath $TomcatHome).Path
    $version = & $java -cp (Join-Path $TomcatHome 'lib\catalina.jar') org.apache.catalina.util.ServerInfo
    if ($LASTEXITCODE -ne 0 -or ($version -join [Environment]::NewLine) -notmatch '(?m)^Server number:\s+10\.1\.') {
        throw 'Set CATALINA_HOME or -TomcatHome to an Apache Tomcat 10.1 installation.'
    }

    $env:OJ_SANDBOX_TYPE = $SandboxType
    if ($SandboxType -eq 'docker') {
        $docker = (Get-Command docker.exe -CommandType Application).Source
        $dockerOs = & $docker info --format '{{.OSType}}'
        if ($LASTEXITCODE -ne 0 -or $dockerOs -ne 'linux') {
            throw 'Start Docker with Linux containers before choosing the Docker sandbox.'
        }
        $installedImages = & $docker image ls --format '{{.Repository}}:{{.Tag}}'
        if ($LASTEXITCODE -ne 0) { throw 'Could not read the Docker image list.' }
        foreach ($image in 'eclipse-temurin:17', 'gcc:13') {
            if ($installedImages -notcontains $image) {
                & $docker pull -- $image
                if ($LASTEXITCODE -ne 0) { throw "Could not pull $image." }
            }
        }
    } else {
        Write-Host 'Process sandbox: submit only trusted local code.'
    }

    $base = Join-Path $runtime 'base'
    foreach ($dir in 'conf', 'logs', 'temp', 'webapps', 'unmapped', 'work') {
        New-Item -ItemType Directory -Path (Join-Path $base $dir) -Force | Out-Null
    }
    foreach ($file in 'context.xml', 'web.xml', 'catalina.properties', 'logging.properties') {
        Copy-Item -LiteralPath (Join-Path $TomcatHome "conf\$file") -Destination (Join-Path $base "conf\$file") -Force
    }
    @"
<?xml version="1.0" encoding="UTF-8"?>
<Server port="-1">
  <Service name="Catalina">
    <Connector address="127.0.0.1" port="$Port" protocol="HTTP/1.1" />
    <Engine name="Catalina" defaultHost="unmapped.invalid">
      <Host name="localhost" appBase="webapps" deployOnStartup="true" autoDeploy="false" unpackWARs="true">
        <Alias>127.0.0.1</Alias>
      </Host>
      <Host name="unmapped.invalid" appBase="unmapped" deployOnStartup="false" autoDeploy="false" />
    </Engine>
  </Service>
</Server>
"@ | Set-Content -LiteralPath (Join-Path $base 'conf\server.xml') -Encoding UTF8

    $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try {
        $probe.Start()
    } finally {
        $probe.Stop()
    }

    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'target\mini-oj.war') -Destination (Join-Path $base 'webapps\ROOT.war') -Force

    $classpath = (Join-Path $TomcatHome 'bin\bootstrap.jar') + ';' + (Join-Path $TomcatHome 'bin\tomcat-juli.jar')
    $startupArgs = @(
        "-Dcatalina.home=$TomcatHome"
        "-Dcatalina.base=$base"
        "-Djava.io.tmpdir=$(Join-Path $base 'temp')"
        "-Djava.util.logging.config.file=$(Join-Path $base 'conf\logging.properties')"
        '-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager'
        '-cp'
        $classpath
        'org.apache.catalina.startup.Bootstrap'
        'start'
    )
    $url = "http://127.0.0.1:$Port/"
    Write-Host "Starting Mini OJ. The browser will open at $url when ready (Ctrl+C to stop)."
    $browserJob = Start-Job -ArgumentList $url -ScriptBlock {
        param([string]$url)

        $deadline = [DateTime]::UtcNow.AddMinutes(10)
        do {
            $page = & curl.exe --disable --noproxy '*' --silent --fail --max-time 2 $url
            if ($LASTEXITCODE -eq 0 -and $page -match '<title>Mini OJ</title>') {
                Start-Process -FilePath $url
                return
            }
            Start-Sleep -Seconds 1
        } while ([DateTime]::UtcNow -lt $deadline)
    }
    try {
        & $java @startupArgs
        if ($LASTEXITCODE -ne 0) { throw "Tomcat exited with code $LASTEXITCODE." }
    } finally {
        Remove-Job -Job $browserJob -Force
    }
} finally {
    Pop-Location
    $instance.Dispose()
}
