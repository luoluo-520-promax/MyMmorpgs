#Requires -Version 5.1
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$docx = "c:\Users\ASUS\Desktop\MyMmorpg项目全方位详细介绍文档.docx"
$bak  = "c:\Users\ASUS\Desktop\MyMmorpg项目全方位详细介绍文档.backup-before-P11.docx"
$payloadPath = Join-Path $env:TEMP "mmorpg-p11-payload.json"
$work = Join-Path $env:TEMP ("mmorpg-docx-p11-" + [guid]::NewGuid().ToString("N"))
$log  = Join-Path $env:TEMP "mmorpg-docx-p11-update.log"
function Log([string]$m){ $line=("[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"),$m); Add-Content -LiteralPath $log -Value $line -Encoding UTF8; Write-Output $line }
if(Test-Path -LiteralPath $log){Remove-Item -LiteralPath $log -Force}
Log "START"
$P = Get-Content -LiteralPath $payloadPath -Encoding UTF8 -Raw | ConvertFrom-Json
Get-Process WINWORD -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
Start-Sleep 1
Copy-Item -LiteralPath $docx -Destination $bak -Force
Log ("BACKUP "+(Get-Item -LiteralPath $bak).Length)
New-Item -ItemType Directory -Path $work | Out-Null
$zipCopy = Join-Path $work "doc.zip"; Copy-Item -LiteralPath $docx -Destination $zipCopy -Force
$extract = Join-Path $work "unzipped"
[System.IO.Compression.ZipFile]::ExtractToDirectory($zipCopy,$extract)
$docXmlPath = Join-Path $extract "word\document.xml"
$script:xml = [System.IO.File]::ReadAllText($docXmlPath,[System.Text.Encoding]::UTF8)
Log ("XML "+$script:xml.Length)
function Esc([string]$s){ return ((($s -replace "&","&amp;") -replace "<","&lt;") -replace ">","&gt;") }
function Para([string]$text,[string]$styleVal){
  $t=Esc $text
  if([string]::IsNullOrEmpty($styleVal)){ return "<w:p><w:r><w:t xml:space=`"preserve`">$t</w:t></w:r></w:p>" }
  return "<w:p><w:pPr><w:pStyle w:val=`"$styleVal`"/></w:pPr><w:r><w:t xml:space=`"preserve`">$t</w:t></w:r></w:p>"
}
function Insert-AfterAnchor([string]$anchor,[string]$block){
  $idx=$script:xml.LastIndexOf($anchor); if($idx -lt 0){Log ("MISS "+$anchor); return $false}
  $end=$script:xml.IndexOf("</w:p>",$idx); if($end -lt 0){throw "no p"}; $end+=6
  $script:xml=$script:xml.Insert($end,$block); return $true
}
function Insert-BeforeAnchor([string]$anchor,[string]$block){
  $idx=$script:xml.IndexOf($anchor); if($idx -lt 0){Log ("MISS_B "+$anchor); return $false}
  $start=$script:xml.LastIndexOf("<w:p",$idx); if($start -lt 0){throw "no p"}
  $script:xml=$script:xml.Insert($start,$block); return $true
}
function Get-Plain([string]$x){ $sb=New-Object System.Text.StringBuilder; foreach($m in [regex]::Matches($x,'(?s)<w:t[^>]*>(.*?)</w:t>')){[void]$sb.Append($m.Groups[1].Value)}; return $sb.ToString() }
function Join-Paras($title,$titleStyle,$paras){
  $xml = Para $title $titleStyle
  foreach($p in $paras){ $xml += (Para ([string]$p) $null) }
  return $xml
}
$overviewXml = Para ([string]$P.overview) $null
$sec327Xml = Join-Paras ([string]$P.h327) "3" $P.p327
$sec529Xml = Join-Paras ([string]$P.h529) "3" $P.p529
$storyXml  = Join-Paras ([string]$P.h113) "2" $P.p113
$q11Xml    = (Para ([string]$P.q11t) "2") + (Para ([string]$P.q11b) $null)
Log ("insO="+ (Insert-AfterAnchor "OpenWorldP10BusinessFlowTest" $overviewXml))
Log ("ins327="+(Insert-BeforeAnchor "ACTION_PERFECT_DODGE" $sec327Xml))
Log ("ins529="+(Insert-BeforeAnchor "战斗负责开战" $sec529Xml))
Log ("ins113="+(Insert-AfterAnchor "ROLLBACK" $storyXml))
Log ("insQ11="+(Insert-AfterAnchor "OpenWorldP10EdgeCaseTest" $q11Xml))
[System.IO.File]::WriteAllText($docXmlPath,$script:xml,[System.Text.UTF8Encoding]::new($false))
$outDocx=Join-Path $work "out.docx"; if(Test-Path $outDocx){Remove-Item $outDocx -Force}
[System.IO.Compression.ZipFile]::CreateFromDirectory($extract,$outDocx,[System.IO.Compression.CompressionLevel]::Optimal,$false)
Copy-Item -LiteralPath $outDocx -Destination $docx -Force
Log ("SIZE "+(Get-Item -LiteralPath $docx).Length)
$word=$null;$doc=$null
try{
  $word=New-Object -ComObject Word.Application; $word.Visible=$false; $word.DisplayAlerts=0
  $doc=$word.Documents.Open($docx,$false,$false)
  function WordReplace($a,$b){ $r=$doc.Content; $f=$r.Find; $null=$f.ClearFormatting(); $null=$f.Replacement.ClearFormatting(); $f.Text=$a; $f.Replacement.Text=$b; $f.Forward=$true; $f.Wrap=1; $ok=$f.Execute($a,$false,$false,$false,$false,$false,$true,1,$false,$b,2); Log ("WR="+[bool]$ok) }
  WordReplace ([string]$P.revOld) ([string]$P.revNew)
  WordReplace ([string]$P.meatOld) ([string]$P.meatNew)
  WordReplace ([string]$P.dateOld) ([string]$P.dateNew)
  WordReplace ([string]$P.docsOld) ([string]$P.docsNew)
  WordReplace ([string]$P.planOld) ([string]$P.planNew)
  WordReplace ([string]$P.sumOld) ([string]$P.sumNew)
  foreach($pair in @(@([string]$P.h327,[string]$P.styleH3),@([string]$P.h529,[string]$P.styleH3),@([string]$P.h113,[string]$P.styleH2),@([string]$P.q11t,[string]$P.styleH2))){
    try{ $rng=$doc.Content.Duplicate; $f=$rng.Find; $null=$f.ClearFormatting(); $f.Text=$pair[0]; $f.Forward=$true; $f.Wrap=0; if($f.Execute()){ $null=$rng.Expand(4); $rng.Style=$pair[1]; Log "styled" } } catch { Log ("styleErr "+$_.Exception.Message) }
  }
  $doc.Save(); Log "saved"
} catch { Log ("COMERR "+$_.Exception.Message) }
finally {
  if($doc){try{$doc.Close([ref]$false)}catch{};[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($doc)}
  if($word){try{$word.Quit()}catch{};[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($word)}
  [GC]::Collect(); [GC]::WaitForPendingFinalizers()
}
$plain=Get-Plain $script:xml
foreach($c in @("P11","3.2.7","5.2.9","11.13","Q11","TeamCompositionService","TERRAIN_EXHAUSTED","StoryStateMachine","HandbookService","RogueFateCardService","MARK_TARGET")){ Log ("V "+$c+"="+$plain.Contains($c)) }
[System.IO.File]::WriteAllText((Join-Path $env:TEMP "mmorpg-doc-plain-after-p11.txt"),$plain,[System.Text.UTF8Encoding]::new($false))
$word2=New-Object -ComObject Word.Application; $word2.Visible=$false; $word2.DisplayAlerts=0
$doc2=$word2.Documents.Open($docx,$false,$true); $full=$doc2.Content.Text; $doc2.Close([ref]$false); $word2.Quit()
[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($doc2); [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($word2); [GC]::Collect()
foreach($c in @("P11","3.2.7","5.2.9","11.13","TeamCompositionService")){ Log ("W "+$c+"="+$full.Contains($c)) }
Log ("rev="+$full.Contains("P11 队伍"))
Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
Log "DONE"