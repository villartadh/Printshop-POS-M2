param(
  [ValidateSet('read','set')][string]$Action = 'read',
  [ValidateSet(100,125,150,175,200,225,250,300,350,400,450,500)][int]$Percent = 100
)
$ErrorActionPreference = 'Stop'
if ($env:POS_WINDOWS_ACCEPTANCE -ne 'disposable-desktop') { throw 'Requires a dedicated disposable Windows desktop.' }
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes, System.Windows.Forms
if ([System.Windows.Forms.Screen]::AllScreens.Count -ne 1) { throw 'Acceptance requires exactly one monitor.' }
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class PosNativeDisplay {
  [DllImport("user32.dll")] public static extern uint GetDpiForWindow(IntPtr window);
}
'@
function Get-DisplayWindow {
  for ($i=0; $i -lt 50; $i++) {
    $settings = Get-Process SystemSettings -ErrorAction SilentlyContinue |
      Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if ($settings) {
      $root = [System.Windows.Automation.AutomationElement]::FromHandle($settings.MainWindowHandle)
      $all = $root.FindAll([System.Windows.Automation.TreeScope]::Descendants,
        [System.Windows.Automation.PropertyCondition]::new(
          [System.Windows.Automation.AutomationElement]::ControlTypeProperty,
          [System.Windows.Automation.ControlType]::ComboBox))
      foreach ($combo in $all) {
        if ($combo.Current.Name -match '^Scale(?:\b|$)') { return @($settings, $combo) }
      }
    }
    Start-Sleep -Milliseconds 200
  }
  throw 'The Windows Display Settings Scale control was unavailable. Use an English Windows 11 console session.'
}
Start-Process 'ms-settings:display'
$settings, $combo = Get-DisplayWindow
if ($Action -eq 'set') {
  $pattern = $combo.GetCurrentPattern([System.Windows.Automation.ExpandCollapsePattern]::Pattern)
  $pattern.Expand()
  try {
    $items = $combo.FindAll([System.Windows.Automation.TreeScope]::Descendants,
      [System.Windows.Automation.PropertyCondition]::new(
        [System.Windows.Automation.AutomationElement]::ControlTypeProperty,
        [System.Windows.Automation.ControlType]::ListItem))
    $item = $items | Where-Object { $_.Current.Name -match "^$Percent\s*%" } | Select-Object -First 1
    if (!$item) { throw "The display does not offer the requested scale $Percent%." }
    $item.GetCurrentPattern([System.Windows.Automation.SelectionItemPattern]::Pattern).Select()
  } finally {
    try { $pattern.Collapse() } catch {}
  }
}
for ($i=0; $i -lt 50; $i++) {
  $settings.Refresh()
  $dpi = [PosNativeDisplay]::GetDpiForWindow($settings.MainWindowHandle)
  if ($dpi -gt 0 -and ($Action -eq 'read' -or ($dpi * 100 -eq $Percent * 96))) {
    [pscustomobject]@{ dpi = $dpi; percent = $dpi * 100 / 96; monitorCount = 1 } | ConvertTo-Json -Compress
    exit 0
  }
  Start-Sleep -Milliseconds 200
}
throw 'The Windows Settings window did not report the requested effective DPI.'
