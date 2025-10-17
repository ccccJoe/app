$content = Get-Content 'app\src\main\java\com\example\sims_android\ui\event\EventFormScreen.kt' -Raw
$openCount = ([regex]::Matches($content, '/\*')).Count
$closeCount = ([regex]::Matches($content, '\*/')).Count
Write-Host "Open comments: $openCount"
Write-Host "Close comments: $closeCount"

if ($openCount -ne $closeCount) {
    Write-Host "ERROR: Unmatched comments found!"
    
    # Find all comment positions
    $openMatches = [regex]::Matches($content, '/\*')
    $closeMatches = [regex]::Matches($content, '\*/')
    
    Write-Host "Open comment positions:"
    foreach ($match in $openMatches) {
        $lineNumber = ($content.Substring(0, $match.Index) -split "`n").Count
        Write-Host "  Line $lineNumber at position $($match.Index)"
    }
    
    Write-Host "Close comment positions:"
    foreach ($match in $closeMatches) {
        $lineNumber = ($content.Substring(0, $match.Index) -split "`n").Count
        Write-Host "  Line $lineNumber at position $($match.Index)"
    }
} else {
    Write-Host "All comments are properly matched."
}