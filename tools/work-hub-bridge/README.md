# Work Hub Bridge

The bridge is a per-user .NET 8 process that scans the locally synced `Work Hub Outbox`, validates versioned JSON, resolves exact normalized project numbers, creates or reuses each date's Daily Note, and performs duplicate-safe Notion upserts by immutable Source ID. It never stores email bodies locally beyond the synced input file and disables request-body logging by design.

## Install

1. Copy `bridge.settings.example.json` to `bridge.settings.json` and enter the four Notion **data source** IDs plus the locally synced outbox path.
2. Run `install.ps1` as the normal Windows user. Administrator access is not required.
3. Enter the narrowly scoped Notion integration token when prompted. It is stored in Windows Credential Manager under `TimeTracker.WorkHub.Notion`.
4. Run `WorkHubBridge.exe bridge.settings.json --once` with fixture files before enabling Power Automate.

The installer publishes a self-contained Windows x64 executable and registers a limited, per-user sign-in task. The running bridge also rescans every 30 seconds so missed filesystem notifications are harmless.

## Outbox

```text
Work Hub Outbox/
  Incoming/Email/
  Incoming/Time/
  Receipts/
  Processed/
  Failed/
```

Writers create `.partial` files and rename them to `.json`. The scanner ignores partial files. Accepted/rejected Source IDs are returned in receipts. Processed files are retained seven days; failed files remain until corrected or discarded.

## Power Automate handoff

Create an immediate and reconciliation flow per configured project folder using only Standard Outlook, Content Conversion, and SharePoint connectors. The immediate flow uses `When a new email arrives (V3)`, the exact folder, Include Attachments = No, Only With Attachments = No, static normalized project number/folder values, HTML-to-text, and a typed JSON object matching `contracts/work-hub/v1/email-record.schema.json`. Secure Inputs/Outputs must be enabled for content-bearing steps. The flow must not mark, flag, move, or delete the message.

The reconciliation flow searches today's messages and, just after midnight, yesterday's messages in the same folder and emits the identical envelope. It must never submit a date earlier than activation. Duplicate triggers and reconciliation are safe because Source ID is the SHA-256 of mailbox key plus RFC Internet Message ID and the bridge always upserts.

Email pages contain sender, recipients, subject, received time, preview, cleaned newest-message body, body status, and best-effort attachment names—never attachment binaries. Protected messages create metadata-only pages. Unknown project numbers go to `Failed` and never create projects or fall back to General / Admin.
