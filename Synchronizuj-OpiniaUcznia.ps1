# Synchronizuj-OpiniaUcznia.ps1
# Safe build, commit and push workflow for Opinia Ucznia.
# Only approved project files are staged. Backups, APKs and secrets are excluded.

$ErrorActionPreference = "Stop"
$ProjectPath = "C:\apki\opiniaucznia"
$ExpectedBranch = "feature/updater-progress"
$RemoteName = "origin"
$RemoteBranch = "feature/updater-progress"

function Stop-Script([string]$Message) {
    Write-Host "`nSTOP: $Message" -ForegroundColor Red
    exit 1
}

function Invoke-Git([string[]]$GitArgs) {
    & git @GitArgs
    if ($LASTEXITCODE -ne 0) {
        Stop-Script ("Git command failed: git " + ($GitArgs -join " "))
    }
}

Set-Location $ProjectPath

Write-Host "`n=== 1. REPOSITORY CHECK ===" -ForegroundColor Cyan
$RepoRoot = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0) { Stop-Script "This folder is not a Git repository." }
$RepoRoot = (Resolve-Path $RepoRoot).Path.TrimEnd('\')
$ExpectedRoot = (Resolve-Path $ProjectPath).Path.TrimEnd('\')
if ($RepoRoot -ne $ExpectedRoot) { Stop-Script "Unexpected repository root: $RepoRoot" }

$Branch = (& git branch --show-current).Trim()
if ($LASTEXITCODE -ne 0) { Stop-Script "Cannot determine current branch." }
if ($Branch -ne $ExpectedBranch) {
    Stop-Script "Current branch is '$Branch'. Expected '$ExpectedBranch'. No branch switching was done."
}
Write-Host "Repository: $RepoRoot"
Write-Host "Branch: $Branch"

# Do not mix this workflow with already staged changes.
& git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
    Stop-Script "There are already staged changes. Review/commit them separately first."
}

# Do not run during an unfinished merge, rebase or cherry-pick.
$GitDir = (& git rev-parse --git-dir).Trim()
$GitDirPath = if ([System.IO.Path]::IsPathRooted($GitDir)) { $GitDir } else { Join-Path $RepoRoot $GitDir }
$InProgressMarkers = @("MERGE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD", "rebase-merge", "rebase-apply")
foreach ($Marker in $InProgressMarkers) {
    if (Test-Path (Join-Path $GitDirPath $Marker)) {
        Stop-Script "Git operation in progress ($Marker). Finish or resolve it manually first."
    }
}

Write-Host "`n=== 2. BUILD DEBUG APK ===" -ForegroundColor Cyan
& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { Stop-Script "Gradle build failed. No commit or push was performed." }
Write-Host "Build successful." -ForegroundColor Green

Write-Host "`n=== 3. FIND CHANGED FILES ===" -ForegroundColor Cyan
$ChangedTracked = @(& git diff --name-only)
if ($LASTEXITCODE -ne 0) { Stop-Script "Could not list modified files." }
$ChangedUntracked = @(& git ls-files --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { Stop-Script "Could not list untracked files." }

$AllChanged = @($ChangedTracked + $ChangedUntracked | Where-Object { $_ -and $_.Trim() } | Sort-Object -Unique)

# Explicit allowlist. Only Kotlin source files directly in the app package,
# the app Gradle file and AndroidManifest are eligible.
$Allowed = @()
$Excluded = @()
foreach ($File in $AllChanged) {
    $IsAllowed = (
        $File -eq "Synchronizuj-OpiniaUcznia.ps1" -or
        $File -eq "Synchronizuj-OpiniaUcznia.ps1" -or
        $File -eq "app/build.gradle.kts" -or
        $File -eq "app/src/main/AndroidManifest.xml" -or
        $File -match '^app/src/main/java/com/example/opiniaucznia/[^/]+\.kt$'
    )
    if ($IsAllowed) { $Allowed += $File } else { $Excluded += $File }
}

if ($Excluded.Count -gt 0) {
    Write-Host "`nFiles excluded from commit (not staged):" -ForegroundColor Yellow
    $Excluded | ForEach-Object { Write-Host "  - $_" }
}

if ($Allowed.Count -eq 0) {
    Write-Host "`nNo changes in the approved file list. Nothing to commit." -ForegroundColor Yellow
    exit 0
}

Write-Host "`nApproved files to stage:" -ForegroundColor Green
$Allowed | ForEach-Object { Write-Host "  + $_" }

$ConfirmFiles = Read-Host "`nType STAGE to stage only these files"
if ($ConfirmFiles -cne "STAGE") { Stop-Script "User cancelled before staging." }

foreach ($File in $Allowed) {
    & git add -- $File
    if ($LASTEXITCODE -ne 0) { Stop-Script "Could not stage: $File" }
}

Write-Host "`n=== 4. REVIEW STAGED CHANGES ===" -ForegroundColor Cyan
& git diff --cached --check
if ($LASTEXITCODE -ne 0) { Stop-Script "Whitespace check failed. Changes remain staged; nothing was committed or pushed." }

& git diff --cached --stat
if ($LASTEXITCODE -ne 0) { Stop-Script "Could not display staged diff." }

Write-Host "`nStaged file list:"
& git diff --cached --name-only
if ($LASTEXITCODE -ne 0) { Stop-Script "Could not list staged files." }

$ConfirmCommit = Read-Host "`nType COMMIT to create a commit with these files"
if ($ConfirmCommit -cne "COMMIT") { Stop-Script "User cancelled before commit. Staged files were left staged." }

$CommitMessage = Read-Host "Enter a short commit message (English or Polish)"
if ([string]::IsNullOrWhiteSpace($CommitMessage)) { Stop-Script "Commit message cannot be empty." }

Write-Host "`n=== 5. CREATE COMMIT ===" -ForegroundColor Cyan
& git commit -m $CommitMessage
if ($LASTEXITCODE -ne 0) { Stop-Script "Commit failed. No push was performed." }

Write-Host "`n=== 6. CHECK REMOTE BEFORE PUSH ===" -ForegroundColor Cyan
Invoke-Git @("fetch", $RemoteName)

$Counts = (& git rev-list --left-right --count "HEAD...$RemoteName/$RemoteBranch")
if ($LASTEXITCODE -ne 0) { Stop-Script "Could not compare local and remote branches." }
$Parts = ($Counts -join " ").Trim() -split '\s+'
if ($Parts.Count -ne 2) { Stop-Script "Unexpected output from git rev-list: $Counts" }

$Ahead = [int]$Parts[0]
$Behind = [int]$Parts[1]
Write-Host "Local-only commits: $Ahead"
Write-Host "Remote-only commits: $Behind"

if ($Behind -gt 0) {
    Stop-Script "GitHub has commits not present locally. The new commit is saved locally, but was NOT pushed. Review the branch before continuing."
}
if ($Ahead -eq 0) {
    Write-Host "Local and remote branches are already synchronized; nothing to push." -ForegroundColor Green
    exit 0
}

$ConfirmPush = Read-Host "`nType PUSH to send local commits to origin/$RemoteBranch"
if ($ConfirmPush -cne "PUSH") { Stop-Script "User cancelled before push. Commit remains local." }

Write-Host "`n=== 7. PUSH TO GITHUB ===" -ForegroundColor Cyan
& git push $RemoteName $RemoteBranch
if ($LASTEXITCODE -ne 0) {
    Stop-Script "Push failed. The local commit is preserved. Do not force-push; inspect the Git error."
}

Write-Host "`n=== 8. FINAL SYNC CHECK ===" -ForegroundColor Cyan
Invoke-Git @("fetch", $RemoteName)
& git status --short --branch
if ($LASTEXITCODE -ne 0) { Stop-Script "Could not display final Git status." }

$FinalCounts = (& git rev-list --left-right --count "HEAD...$RemoteName/$RemoteBranch")
if ($LASTEXITCODE -ne 0) { Stop-Script "Could not verify final synchronization." }
$FinalParts = ($FinalCounts -join " ").Trim() -split '\s+'
if ($FinalParts.Count -ne 2) { Stop-Script "Unexpected final comparison output." }

if ([int]$FinalParts[0] -eq 0 -and [int]$FinalParts[1] -eq 0) {
    Write-Host "`nSUCCESS: local branch and GitHub are synchronized (0 0)." -ForegroundColor Green
} else {
    Write-Host "`nWARNING: branches are not fully synchronized. Do not force-push." -ForegroundColor Yellow
    Write-Host "HEAD...origin: $FinalCounts"
    exit 1
}
