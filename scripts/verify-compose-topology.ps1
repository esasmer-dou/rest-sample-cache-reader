#Requires -Version 7.0
[CmdletBinding()]
param([switch]$Prepare, [switch]$NativeProvider, [switch]$Keep, [switch]$ConfigOnly)
# JDK 21/Maven and resolvable checkout dependencies are needed only for -Prepare.
# Full: ./scripts/verify-compose-topology.ps1 -Prepare
# Native catalog-only: ./scripts/verify-compose-topology.ps1 -Prepare -NativeProvider
# Static only: ./scripts/verify-compose-topology.ps1 -ConfigOnly
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$topology = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../docker/compose'))
$docker = (Get-Command docker -ErrorAction Stop).Source
$project = 'wp3-' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$compose = @('compose', '-p', $project, '-f', (Join-Path $topology 'docker-compose.yml'))
if ($NativeProvider) { $compose += @('-f', (Join-Path $topology 'native-provider.yml')) }

function Invoke-Docker {
    param([string[]]$Command, [int]$TimeoutSeconds = 30, [string]$InputText = '')
    $process = [Diagnostics.Process]::new()
    try {
        $process.StartInfo.FileName = $docker
        $process.StartInfo.UseShellExecute = $false
        $process.StartInfo.RedirectStandardOutput = $true
        $process.StartInfo.RedirectStandardError = $true
        $process.StartInfo.RedirectStandardInput = $true
        foreach ($argument in $Command) { $process.StartInfo.ArgumentList.Add($argument) }
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if ($InputText) { $process.StandardInput.Write($InputText) }
        $process.StandardInput.Close()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            $process.Kill($true)
            throw "Docker command exceeded ${TimeoutSeconds}s: $($Command -join ' ')"
        }
        $output = $stdout.GetAwaiter().GetResult()
        $errors = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "Docker exit $($process.ExitCode): $errors $output" }
        if ($errors) { Write-Verbose $errors.Trim() }
        return $output.Trim()
    } finally { $process.Dispose() }
}
function Invoke-Compose {
    param([string[]]$Command, [int]$TimeoutSeconds = 30, [string]$InputText = '')
    Invoke-Docker -Command ($compose + $Command) -TimeoutSeconds $TimeoutSeconds -InputText $InputText
}
if ($ConfigOnly) { Invoke-Compose -Command @('config', '--quiet'); return }
if ($Prepare) {
    $maven = (Get-Command mvn -ErrorAction Stop).Source
    foreach ($repo in @('rest-sample-cache-reader', 'rest-sample-cache-writer', 'rest-sample-dubbo-provider', 'rest-sample-dubbo-consumer')) {
        $profile = @(); $output = 'target'
        if ($repo -eq 'rest-sample-dubbo-provider') {
            if ($NativeProvider) { $profile = @('-Pnative-provider'); $output = 'target/native-provider' }
            else { $profile = @('-Pfull-provider') }
        }
        if ($repo -eq 'rest-sample-dubbo-consumer') {
            if ($NativeProvider) { $profile = @('-Pnative-static-consumer') }
            else { $profile = @('-Pfull-dubbo-consumer') }
        }
        $mavenArguments = @('-B', '-ntp', '-f', (Join-Path $workspace "$repo/pom.xml")) + $profile + @('-DskipTests', 'clean', 'package', 'dependency:copy-dependencies', '-DincludeScope=runtime', "-DoutputDirectory=$workspace/$repo/$output/dependency")
        & $maven @mavenArguments
        if ($LASTEXITCODE -ne 0) { throw "Artifact preparation failed: $repo" }
    }
}
$evidence = Join-Path $workspace ".tmp/$project"
[void][IO.Directory]::CreateDirectory($evidence)
$started = $false; $success = $false; $cleanupFailed = $false
$services = @('postgres', 'redis', 'cache-writer', 'cache-reader', 'dubbo-provider', 'dubbo-consumer')
$readerPort = if ($env:READER_PORT) { $env:READER_PORT } else { '18081' }
$consumerPort = if ($env:CONSUMER_PORT) { $env:CONSUMER_PORT } else { '18082' }
$reader = "http://127.0.0.1:$readerPort"
$consumer = "http://127.0.0.1:$consumerPort"
function Request([string]$Uri, [string]$Method = 'GET', [string]$Body = '') {
    $parameters = @{Uri = $Uri; Method = $Method; TimeoutSec = 5; MaximumRedirection = 0}
    if ($Body) { $parameters.ContentType = 'application/json'; $parameters.Body = $Body }
    (Invoke-WebRequest @parameters).Content
}
function Sql([string]$Statement) {
    Invoke-Compose -Command @('exec', '-T', 'postgres', 'psql', '-U', 'reactor', '-d', 'reactor_sample', '-v', 'ON_ERROR_STOP=1', '-Atqc', $Statement)
}
function Redis([string[]]$RedisArguments) {
    Invoke-Compose -Command (@('exec', '-T', 'redis', 'redis-cli', '--raw') + $RedisArguments)
}
function Assert-Customer([string]$Json, [string]$CustomerNo, [string]$Segment) {
    $null = $Json | ConvertFrom-Json
    if ($Json -notmatch ('"customerNo"\s*:\s*"' + [regex]::Escape($CustomerNo) + '"') -or
        $Json -notmatch ('"segment"\s*:\s*"' + [regex]::Escape($Segment) + '"')) {
        throw "JSON customer/segment mismatch: $CustomerNo/$Segment"
    }
}
function Wait-Cache([long]$CustomerId, [string]$CustomerNo, [string]$Segment) {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    do {
        try {
            $body = Request "$reader/api/v1/cache/customers/$CustomerId"
            Assert-Customer $body $CustomerNo $Segment
            $body | Set-Content (Join-Path $evidence "cache-$Segment.json")
            return
        } catch {
            if ($timer.Elapsed.TotalSeconds -ge 40) { throw "Cache projection failed to converge: $CustomerId/$Segment; $_" }
            Start-Sleep -Seconds 1
        }
    } while ($true)
}
try {
    Invoke-Compose -Command @('config') | Set-Content (Join-Path $evidence 'compose.resolved.yml')
    Invoke-Compose -Command @('build') -TimeoutSeconds 600 | Set-Content (Join-Path $evidence 'build.log')
    $started = $true
    Invoke-Compose -Command @('up', '-d', '--wait', '--wait-timeout', '180') -TimeoutSeconds 210 | Set-Content (Join-Path $evidence 'startup.log')
    foreach ($service in $services) {
        $cid = Invoke-Compose -Command @('ps', '-q', $service)
        if (-not $cid) { throw "Missing service: $service" }
        $inspection = @(Invoke-Docker -Command @('inspect', $cid) | ConvertFrom-Json)[0]
        if ($inspection.HostConfig.Memory -ne 134217728 -or $inspection.HostConfig.NanoCpus -ne 1000000000 -or
            $inspection.State.Health.Status -ne 'healthy' -or $inspection.State.OOMKilled) {
            throw "$service failed 1 CPU / 128 MiB / healthy / non-OOM check"
        }
    }
    Request "$reader/app/health" | Set-Content (Join-Path $evidence 'reader-health.json')
    Request "$reader/app/readiness" | Set-Content (Join-Path $evidence 'reader-ready.json')
    Request "$consumer/app/health" | Set-Content (Join-Path $evidence 'consumer-health.json')
    Request "$consumer/app/ready" | Set-Content (Join-Path $evidence 'consumer-ready.json')
    $catalog = Request "$consumer/api/v1/catalog/nested"
    $null = $catalog | ConvertFrom-Json
    $catalog | Set-Content (Join-Path $evidence 'catalog.json')
    if ($NativeProvider) {
        Write-Output 'Native catalog RPC only; SQL mutations below bypass the provider (no customer command/DB RPC support).'
        $seedId = Sql "select id from sample_customers where customer_no='CUST-1001'"
        $seed = Request "$reader/api/v1/cache/customers/$seedId"
        Assert-Customer $seed 'CUST-1001' 'pilot'
        $seed | Set-Content (Join-Path $evidence 'seed-cache.json')
    }
    $customerNo = 'WP3-' + [guid]::NewGuid().ToString('N').Substring(0, 20)
    $before = Redis @('GET', 'crm.customer.detail:current')
    if ($before -notmatch '^([1-9][0-9]*)\|.+$') { throw 'No fenced detail pointer before mutation' }
    $beforeFence = [long]$Matches[1]
    if ($NativeProvider) {
        Sql "insert into sample_customers (customer_no,full_name,segment,email) values ('$customerNo','Compose Smoke','wp3-created','smoke@example.invalid') returning id" | Set-Content (Join-Path $evidence 'native-sql-create.txt')
    } else {
        $body = @{customerNo=$customerNo; fullName='Compose Smoke'; segment='wp3-created'; email='smoke@example.invalid'; requestId=$customerNo} | ConvertTo-Json -Compress
        Request "$consumer/api/v1/customers" 'POST' $body | Set-Content (Join-Path $evidence 'create.json')
    }
    $customerId = Sql "select id from sample_customers where customer_no='$customerNo' and segment='wp3-created'"
    if ($customerId -notmatch '^[1-9][0-9]*$') { throw 'Create did not persist expected PostgreSQL row' }
    if (-not $NativeProvider) {
        Assert-Customer (Request "$consumer/api/v1/customers/db/$customerId") $customerNo 'wp3-created'
    }
    Wait-Cache $customerId $customerNo 'wp3-created'
    if ($NativeProvider) {
        Sql "update sample_customers set segment='wp3-patched',updated_at=now() where id=$customerId returning id" | Set-Content (Join-Path $evidence 'native-sql-patch.txt')
    } else {
        Request "$consumer/api/v1/customers/$customerId/segment" 'PATCH' '{"segment":"wp3-patched","requestId":"wp3-segment"}' | Set-Content (Join-Path $evidence 'patch.json')
    }
    if ((Sql "select segment from sample_customers where id=$customerId") -ne 'wp3-patched') { throw 'Mutation did not persist PostgreSQL segment' }
    if (-not $NativeProvider) {
        Assert-Customer (Request "$consumer/api/v1/customers/db/$customerId") $customerNo 'wp3-patched'
    }
    Wait-Cache $customerId $customerNo 'wp3-patched'
    $pointer = Redis @('GET', 'crm.customer.detail:current')
    if ($pointer -notmatch '^([1-9][0-9]*)\|(.+)$') { throw 'Malformed fenced pointer' }
    $fence = [long]$Matches[1]; $version = $Matches[2]
    if ($fence -le $beforeFence) { throw 'Fencing token did not advance' }
    $key = "crm.customer.detail:v:${version}:id:$customerId"
    Assert-Customer (Redis @('GET', $key)) $customerNo 'wp3-patched'
    if ([long](Redis @('PTTL', $key)) -le 0) { throw 'Snapshot value has no live TTL' }
    $pointer | Set-Content (Join-Path $evidence 'fenced-pointer.txt')
    if ($NativeProvider) {
        Sql "delete from sample_customers where id=$customerId returning id" | Set-Content (Join-Path $evidence 'native-sql-delete.txt')
    } else {
        Request "$consumer/api/v1/customers/$customerId" 'DELETE' '{}' | Set-Content (Join-Path $evidence 'delete.json')
    }
    if ((Sql "select count(*) from sample_customers where id=$customerId") -ne '0') { throw 'Smoke row cleanup failed' }
    $rssScript = (Get-Content -Raw (Join-Path $topology 'resident-kib.sh')).Replace("`r`n", "`n")
    'sample,service,pid1_rss_kib,cgroup_resident_kib' | Set-Content (Join-Path $evidence 'rss.csv')
    foreach ($sample in 1..5) {
        foreach ($service in $services) {
            $rss = Invoke-Compose -Command @('exec', '-T', $service, 'sh', '-s') -InputText $rssScript
            if ($rss -notmatch '^(\d+) (\d+)$') { throw "Invalid RSS evidence for ${service}: $rss" }
            $pidRss = [long]$Matches[1]; $resident = [long]$Matches[2]
            "$sample,$service,$pidRss,$resident" | Add-Content (Join-Path $evidence 'rss.csv')
            if ($pidRss -ge 131072 -or $resident -ge 131072) { throw "$service exceeds strict 128 MiB RSS gate" }
        }
        Start-Sleep -Seconds 1
    }
    $success = $true
    Write-Output 'Topology smoke PASS; five bounded RSS samples are NOT sustained-load or p99 qualification.'
} catch {
    $_ | Out-String | Set-Content (Join-Path $evidence 'failure.txt')
    throw
} finally {
    if ($started) {
        try { Invoke-Compose -Command @('ps', '-a') | Set-Content (Join-Path $evidence 'containers.txt') } catch { Write-Warning $_ }
        try { Invoke-Compose -Command @('logs', '--no-color', '--tail', '200') | Set-Content (Join-Path $evidence 'containers.log') } catch { Write-Warning $_ }
        if (-not $Keep) {
            try { Invoke-Compose -Command @('down', '--volumes', '--remove-orphans') | Set-Content (Join-Path $evidence 'cleanup.log') }
            catch { $cleanupFailed = $true; Write-Warning $_ }
        }
    }
    $exitCode = if ($success -and -not $cleanupFailed) { 0 } else { 1 }
    $mode = if ($NativeProvider) { 'native-catalog-only' } else { 'full' }
    @("exit=$exitCode", "mode=$mode") | Set-Content (Join-Path $evidence 'result.properties')
    Write-Output "Evidence: $evidence"
    if ($cleanupFailed) { throw 'Own Compose stack cleanup failed; see evidence' }
}
