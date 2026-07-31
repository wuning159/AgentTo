$ErrorActionPreference = 'Stop'
$baseUrl = 'http://127.0.0.1:18473'
$confirmation = Read-Host 'Type DELETE-RAG-TEST-DATA to clean RAG integration data'
if ($confirmation -ne 'DELETE-RAG-TEST-DATA') {
    throw 'Cleanup cancelled: confirmation does not match'
}

$username = Read-Host 'RAG admin username (default: admin)'
if ([string]::IsNullOrWhiteSpace($username)) {
    $username = 'admin'
}
$securePassword = Read-Host 'RAG admin password' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $loginBody = @{ username = $username; password = $password } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
    $headers = @{ Authorization = "Bearer $($login.data.token)" }
    $cleanupBody = @{ confirmation = $confirmation } | ConvertTo-Json
    $result = Invoke-RestMethod -Uri "$baseUrl/api/admin/maintenance/cleanup" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $cleanupBody
    $result.data | Format-List
} finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}
