param(
    [Parameter(Mandatory = $true)] [string] $RimeDictionary,
    [Parameter(Mandatory = $true)] [string] $ChaiziData,
    [Parameter(Mandatory = $true)] [string] $AssetDirectory
)

$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$rimeRows = New-Object 'System.Collections.Generic.List[string]'
$rimeRows.Add('# derived from rime-pinyin-simp; see THIRD_PARTY_NOTICES.md')
$pronunciations = @{}
$frequencies = @{}
$insideDictionary = $false

foreach ($line in [IO.File]::ReadLines($RimeDictionary, [Text.Encoding]::UTF8)) {
    if ($line -eq '...') {
        $insideDictionary = $true
        continue
    }
    if (-not $insideDictionary -or [string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
        continue
    }
    $fields = $line.Split("`t")
    if ($fields.Count -lt 3) { continue }
    $text = $fields[0].Trim()
    $displayPinyin = $fields[1].Trim()
    $key = ($displayPinyin -replace '[^A-Za-z]', '').ToLowerInvariant()
    $frequency = 0
    [void][int]::TryParse($fields[2].Trim(), [ref]$frequency)
    if ($text -eq '' -or $key -eq '') { continue }
    $rimeRows.Add("$key`t$text`t$displayPinyin`t$frequency")

    if ($text.Length -eq 1 -and -not $displayPinyin.Contains(' ')) {
        $currentFrequency = if ($frequencies.ContainsKey($text)) { $frequencies[$text] } else { -1 }
        if ($frequency -gt $currentFrequency) {
            $pronunciations[$text] = $displayPinyin
            $frequencies[$text] = $frequency
        }
    }
}

[IO.File]::WriteAllLines((Join-Path $AssetDirectory 'pinyin_rime.tsv'), $rimeRows, $utf8)

$commonCharacters = New-Object 'System.Collections.Generic.List[string]'
$gb2312 = [Text.Encoding]::GetEncoding(936)
for ($lead = 0xB0; $lead -le 0xD7; $lead++) {
    $lastTrail = if ($lead -eq 0xD7) { 0xF9 } else { 0xFE }
    for ($trail = 0xA1; $trail -le $lastTrail; $trail++) {
        $character = $gb2312.GetString([byte[]]@($lead, $trail))
        if ($character -ne [char]0xFFFD -and $character -ne '?') {
            $commonCharacters.Add($character)
        }
    }
}
[IO.File]::WriteAllLines((Join-Path $AssetDirectory 'common_hanzi.txt'), $commonCharacters, $utf8)

$assemblyRows = New-Object 'System.Collections.Generic.List[string]'
$assemblyRows.Add('# generated from hanzi_chaizi simplified data; see THIRD_PARTY_NOTICES.md')
$seen = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($line in [IO.File]::ReadLines($ChaiziData, [Text.Encoding]::UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $fields = $line.Split("`t")
    if ($fields.Count -lt 2) { continue }
    $target = $fields[0].Trim()
    if (-not $pronunciations.ContainsKey($target)) { continue }
    for ($index = 1; $index -lt $fields.Count; $index++) {
        $components = @($fields[$index].Trim().Split(' ', [StringSplitOptions]::RemoveEmptyEntries))
        if ($components.Count -lt 2) { continue }
        $componentPinyin = New-Object 'System.Collections.Generic.List[string]'
        $complete = $true
        foreach ($component in $components) {
            if (-not $pronunciations.ContainsKey($component)) {
                $complete = $false
                break
            }
            $componentPinyin.Add($pronunciations[$component])
        }
        if (-not $complete) { continue }
        $key = (($componentPinyin -join '') -replace '[^A-Za-z]', '').ToLowerInvariant()
        $identity = "$key|$target"
        if ($key -eq '' -or -not $seen.Add($identity)) { continue }
        $targetFrequency = if ($frequencies.ContainsKey($target)) { $frequencies[$target] } else { 0 }
        $assemblyRows.Add("$key`t$target`t$($pronunciations[$target])`t$($components -join '+')`t$targetFrequency")
    }
}

[IO.File]::WriteAllLines((Join-Path $AssetDirectory 'assembly_full.tsv'), $assemblyRows, $utf8)
Write-Output "Pinyin rows: $($rimeRows.Count - 1)"
Write-Output "Assembly rows: $($assemblyRows.Count - 1)"
Write-Output "Common characters: $($commonCharacters.Count)"
