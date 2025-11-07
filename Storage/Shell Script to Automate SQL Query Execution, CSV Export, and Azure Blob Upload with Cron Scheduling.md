Here’s a professional `README.md` you can use for your project/script:

````markdown
# SQL → CSV → Azure Blob Automation

This repository contains a Bash script to automate exporting SQL Server query results to a CSV file and uploading it to an Azure Blob Storage container. The script can be scheduled via `cron` to run at regular intervals.

---

## Features

- Connects to a SQL Server database using `sqlcmd`.
- Exports query results to a CSV file.
- Creates an Azure Blob container if it does not exist.
- Uploads the CSV file to the specified Azure Blob container.
- Logs success or failure for each run.
- Can be scheduled via `cron` for automated execution.

---

## Prerequisites

- Ubuntu VM (tested on 22.04/24.04)
- `sqlcmd` installed:
  ```bash
  sudo apt update
  sudo apt install -y mssql-tools unixodbc-dev
````

* Azure CLI installed and logged in:

  ```bash
  az login
  ```
* Permissions:

  * SQL Server credentials (`sqladmin` or other)
  * Azure Storage account access
* Optional: SSH key for downloading exported files via `scp`

---

## Setup

1. **Create the script file**

```bash
sudo vim /home/jenkinsadmin/export_to_blob.sh
```

Paste the following content:

```bash
#!/bin/bash
# ================================
# SQL → CSV → Azure Blob Automation
# ================================

set -e

# -----------------------
# Variables
# -----------------------
STORAGE_ACCOUNT="codadevsa"
CONTAINER_NAME="codadev"       # change to: codadev | codaqa | codauat
REPORTS_DIR="reports"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
OUTPUT_DIR="/home/jenkinsadmin/sql_export_output"
LOG_FILE="$OUTPUT_DIR/export_to_blob.log"

SQL_SERVER="test-coda.database.windows.net"
DB_NAME="CODA-2024-5-8-12-4-DEV"
DB_USER="sqladmin"
DB_PASS="M*"

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

OUTPUT_FILE="$OUTPUT_DIR/output_${TIMESTAMP}.csv"

# Full path to sqlcmd (avoid PATH issues in cron)
SQLCMD="/opt/mssql-tools/bin/sqlcmd"

# -----------------------
# Run the SQL query and export results
# -----------------------
$SQLCMD -S "$SQL_SERVER" \
       -d "$DB_NAME" \
       -U "$DB_USER" \
       -P "$DB_PASS" \
       -Q "SELECT TransactionType, SRTitleHeaderID, AVTitleHeaderID, SRTH.Title, SRTH.Artist, PerfType, SSN, [Name] AS PerformerName, [Account#], [Perf_VMA], CntrbAmt, [DeductionPCTAmt] AS DeductAmt, CntbCnt, NetAmt, CntrbShrAmt, [DedShrAmt] AS DeductShrAmt, NetShrAmt, OtherRole, AVSRSec, AVUnSec, AVUnAmtNF, AVSRAmtNF, AVUnAmtF, AVSRAmtF, AVUnNFShr, AVSRNFShr, FiscYr, DistrDate, Src, SrcYear, TransactionCode, BusinessID, RunDate, RunTime, UserID, ParticipantHeaderID, ACCT.IsActive, Acct.CreatedDate, Acct.CreatedBy, FileManagerID, RunID, Acct.ModifiedBy, Acct.ModifiedDate, SrcRunDeptID, SrcRunID, DistQueueID, PLHeaderID, PFM.BusinessFileManagerID, PFM.FileManagerDescription, SRTH.BusinessTitleID FROM Account.DRAllocationAcct ACCT INNER JOIN PlayList.FileManager PFM ON ACCT.FileManagerID=PFM.ID AND PFM.IsActive=1 INNER JOIN Title.SRTitleHeader SRTH ON ACCT.SRTitleHeaderID=SRTH.ID AND SRTH.IsActive=1 WHERE ACCT.IsActive=1" \
       -o "$OUTPUT_FILE" -s "," -W

# -----------------------
# Create container if not exists
# -----------------------
az storage container create \
  --name "$CONTAINER_NAME" \
  --account-name "$STORAGE_ACCOUNT" \
  --auth-mode login -o none

# -----------------------
# Ensure 'reports' folder exists (simulated in blob)
# -----------------------
if ! az storage blob exists \
    --account-name "$STORAGE_ACCOUNT" \
    --container-name "$CONTAINER_NAME" \
    --name "$REPORTS_DIR/.keep" \
    --auth-mode login -o tsv | grep -q True; then

    az storage blob upload \
      --account-name "$STORAGE_ACCOUNT" \
      --container-name "$CONTAINER_NAME" \
      --name "$REPORTS_DIR/.keep" \
      --file /dev/null \
      --auth-mode login -o none
fi

# -----------------------
# Upload to Azure Blob (inside reports folder)
# -----------------------

if az storage blob upload \
    --account-name "$STORAGE_ACCOUNT" \
    --container-name "$CONTAINER_NAME" \
    --name "$REPORTS_DIR/$(basename "$OUTPUT_FILE")" \
    --file "$OUTPUT_FILE" \
    --auth-mode login \
    -o none; then

    echo "[$(date)] ✅ Uploaded $(basename "$OUTPUT_FILE") to '$CONTAINER_NAME/$REPORTS_DIR' in '$STORAGE_ACCOUNT'" >> "$LOG_FILE"
    rm -f "$OUTPUT_FILE"
else
    echo "[$(date)] ❌ Upload failed for $OUTPUT_FILE" >> "$LOG_FILE"
fi
```

2. **Make the script executable**

```bash
chmod +x /home/jenkinsadmin/export_to_blob.sh
```

---

## Scheduling with Cron

1. Open crontab:

```bash
crontab -e
```

2. Add a line to run the script every minute:

```bash
* * * * * /home/jenkinsadmin/export_to_blob.sh >> /home/jenkinsadmin/export_to_blob_cron.log 2>&1
```

3. Save and exit. Check scheduled jobs:

```bash
crontab -l
```

---

## Manual Testing

Run the script manually to verify functionality:

```bash
sudo /home/jenkinsadmin/export_to_blob.sh
```

Check logs:

```bash
tail -f /home/jenkinsadmin/export_to_blob_cron.log
tail -f /home/jenkinsadmin/sql_export_output/export_to_blob.log
```

---

## Verify in Azure Blob

List files in container:

```bash
az storage blob list --account-name codadevsa --container-name sample --auth-mode login -o table
```

Download file locally:

```bash
az storage blob download \
    --account-name codadevsa \
    --container-name sample \
    --name "output_20251106_121954.csv" \
    --file "./output_20251106_121954.csv" \
    --auth-mode login
```

Or use `scp`:

```bash
scp -i /c/Aftra/Jenkins/jekins-buildserver.pem jenkinsadmin@10.0.1.11:/home/jenkinsadmin/output_20251106_121954.csv .
```

---

## Notes

* Make sure the SQL Server credentials and Azure Storage account have the necessary permissions.
* Use the full path to `sqlcmd` to avoid PATH issues when running via cron or sudo.
* Logs are maintained in `$OUTPUT_DIR/export_to_blob.log` for success/failure tracking.

```

---

I can also **make a shorter, clean version suitable for GitHub** with proper badges and headings if you want.  

Do you want me to do that?
```
