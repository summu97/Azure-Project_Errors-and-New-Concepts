Here’s a **README.md** that directly reflects exactly what you provided, without extra explanations:

```markdown
SQL_Report_Scripts/
├── export_DEVSQL_reports.sh
├── export_QASQL_reports.sh
├── export_UATSQL_reports.sh
├── report_logs
│   ├── DEVSQL_reports.log
│   ├── QASQL_reports.log
│   ├── UATSQL_reports.log
│   └── cron_DEVSQL.log
└── reports_output

```




## Steps to Set Up

### 1️⃣ Create the folder structure
```bash
mkdir -p /home/jenkinsadmin/SQL_Report_Scripts/report_logs
```

### 2️⃣ Move into the main folder

```bash
cd /home/jenkinsadmin/SQL_Report_Scripts
```

### 3️⃣ Create the export script

```bash
vim export_UATSQL_reports.sh
```

#### Sample Script: `export_UATSQL_reports.sh`

```bash
#!/bin/bash
# ================================================
# SQL → CSV → Upload to Azure Blob Storage (QA)
# ======================================================

set -e  # Exit immediately if a command exits with a non-zero status

# -----------------------
# Configuration Variables
# -----------------------
STORAGE_ACCOUNT="codadevsa"
CONTAINER_NAME="codauat"
REPORTS_DIR="reports"
SUBFOLDERS=("Unclaimed_Ending_Balance" "Unclaimed_Activities" "Playlist_MatchTypes")
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
OUTPUT_DIR="/home/jenkinsadmin/SQL_Report_Scripts/reports_output"
LOG_FILE="/home/jenkinsadmin/SQL_Report_Scripts/report_logs/UATSQL_reports.log"
SQLCMD="/opt/mssql-tools/bin/sqlcmd"
ENV="UAT"

SQL_SERVER="codauatdbserver.database.windows.net"
DB_NAME="CODA_MIGRATION"
KEYVAULT_NAME="CODAUAT-KEYVAULT"  # Your Azure Key Vault name

# -----------------------
# Fetch database credentials from Key Vault
# -----------------------
DB_USER=$(az keyvault secret show --name "SQL${ENV}USER" --vault-name "$KEYVAULT_NAME" --query "value" -o tsv)
DB_PASS=$(az keyvault secret show --name "SQL${ENV}PASSWORD" --vault-name "$KEYVAULT_NAME" --query "value" -o tsv)

# -----------------------
# Ensure local output directory exists
# -----------------------
mkdir -p "$OUTPUT_DIR"


# -----------------------
# Report file definitions
# -----------------------
declare -A REPORTS
REPORTS["Unclaimed_Ending_Balance"]="$OUTPUT_DIR/Unclaimed_Ending_Balance_${ENV}_${TIMESTAMP}.csv"
REPORTS["Unclaimed_Activities"]="$OUTPUT_DIR/Unclaimed_Activities_${ENV}_${TIMESTAMP}.csv"
REPORTS["Playlist_MatchTypes"]="$OUTPUT_DIR/Playlist_MatchTypes_${ENV}_${TIMESTAMP}.csv"

# -----------------------
# Ensure container exists
# -----------------------
az storage container create \
  --name "$CONTAINER_NAME" \
  --account-name "$STORAGE_ACCOUNT" \
  --auth-mode login -o none


# -----------------------
# Ensure 'reports' root folder exists
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
# Loop through and create subfolders
# -----------------------
for SUB in "${SUBFOLDERS[@]}"; do
  SUB_PATH="$REPORTS_DIR/$SUB/.keep"

  if ! az storage blob exists \
      --account-name "$STORAGE_ACCOUNT" \
      --container-name "$CONTAINER_NAME" \
      --name "$SUB_PATH" \
      --auth-mode login -o tsv | grep -q True; then
      az storage blob upload \
        --account-name "$STORAGE_ACCOUNT" \
        --container-name "$CONTAINER_NAME" \
        --name "$SUB_PATH" \
        --file /dev/null \
        --auth-mode login -o none

      echo "✅ Created folder: $REPORTS_DIR/$SUB"
  else
      echo "ℹ️ Folder already exists: $REPORTS_DIR/$SUB"
  fi
done

# -----------------------
# Run SQL query and export to CSV
# -----------------------

# UnClaimed Ending Balance Report
$SQLCMD -S "$SQL_SERVER" \
       -d "$DB_NAME" \
       -U "$DB_USER" \
       -P "$DB_PASS" \
       -Q "SELECT [LKPA].[Description] AS [Transaction Type], [FM].BusinessFileManagerId AS [Source], [PH].[Account#] AS [Participant Account#], COALESCE(PH.LastName, '') + ', ' + COALESCE(PH.FirstName, '') + ' ' + COALESCE(PH.MiddleName, '') AS [Participant Name], [UPIV].[Amt] AS [Amount], [LKPB].[KeyCode] AS [Held Code], [LKPB].[Description] AS [Held Code Description], [UPI].[HeldDate] AS [Held Date] FROM [Payment].[UnClaimedPaymentIdentifierValue] UPIV WITH (NOLOCK) INNER JOIN [Payment].[UnClaimedPaymentIdentifier] UPI WITH (NOLOCK) ON UPI.ID = UPIV.UnClaimedPaymentIdentifierID INNER JOIN [Dist].[DistQueue] DQ WITH (NOLOCK) ON DQ.ID = UPI.DistQueueID INNER JOIN [dbo].[Lookup] LKPA WITH (NOLOCK) ON LKPA.LookupId = DQ.DistTransTypeID INNER JOIN [dbo].[Lookup] LKPB WITH (NOLOCK) ON LKPB.LookupId = UPI.HeldCodeID INNER JOIN [PlayList].[FileManager] FM WITH (NOLOCK) ON FM.ID = UPIV.FileManagerID INNER JOIN [Participant].[ParticipantHeader] PH WITH (NOLOCK) ON PH.ID = UPIV.ParticipantHeaderID WHERE UPIV.IsActive = 1 AND UPI.IsActive = 1 AND UPI.IsReleased = 0 AND LKPA.IsActive = 1 AND LKPB.IsActive = 1 AND FM.IsActive = 1 AND PH.IsActive = 1;" \
       -o "${REPORTS[Unclaimed_Ending_Balance]}" -s "," -W

# UnClaimed Activities Report
$SQLCMD -S "$SQL_SERVER" \
       -d "$DB_NAME" \
       -U "$DB_USER" \
       -P "$DB_PASS" \
       -Q "SELECT [LKPA].[Description] AS [Transaction Type], [FM].[BusinessFileManagerId] AS [Source], [PH].[Account#] AS [Participant Account#], COALESCE(PH.LastName, '') + ', ' + COALESCE(PH.FirstName, '') + ' ' + COALESCE(PH.MiddleName, '') AS [Participant Name], [UPIV].[Amt] AS [Amount], [LKPB].[KeyCode] AS [Code], [LKPB].[Description] AS [Code Description], [UPI].[HeldDate] AS [Held Date], CASE WHEN [UPI].[IsReleased] = 1 THEN 'TRUE' ELSE 'FALSE' END AS [Amount Released], [UPI].[ReleaseDate] AS [Release Date] FROM [Payment].[UnClaimedPaymentIdentifierValue] UPIV WITH (NOLOCK) INNER JOIN [Payment].[UnClaimedPaymentIdentifier] UPI WITH (NOLOCK) ON UPI.ID = UPIV.UnClaimedPaymentIdentifierID INNER JOIN [Dist].[DistQueue] DQ WITH (NOLOCK) ON DQ.ID = UPI.DistQueueID INNER JOIN [dbo].[Lookup] LKPA WITH (NOLOCK) ON LKPA.LookupId = DQ.DistTransTypeID INNER JOIN [dbo].[Lookup] LKPB WITH (NOLOCK) ON LKPB.LookupId = UPI.HeldCodeID INNER JOIN [PlayList].[FileManager] FM WITH (NOLOCK) ON FM.ID = UPIV.FileManagerID INNER JOIN [Participant].[ParticipantHeader] PH WITH (NOLOCK) ON PH.ID = UPIV.ParticipantHeaderID WHERE UPIV.IsActive = 1 AND UPI.IsActive = 1 AND LKPA.IsActive = 1 AND LKPB.IsActive = 1 AND FM.IsActive = 1 AND PH.IsActive = 1;" \
       -o "${REPORTS[Unclaimed_Activities]}" -s "," -W

# Playlist Match Type Results
$SQLCMD -S "$SQL_SERVER" \
       -d "$DB_NAME" \
       -U "$DB_USER" \
       -P "$DB_PASS" \
       -Q "WITH MatchTypeCounts AS (SELECT ph.FileManagerID, ph.PLMatchTypeID, COUNT(ph.ID) AS Count, SUM(ph.PlaylistAmount) AS TotalAmount FROM [Playlist].[PlaylistHeader] ph GROUP BY ph.FileManagerID, ph.PLMatchTypeID), TotalCounts AS (SELECT FileManagerID, SUM(Count) AS TotalRecords, SUM(TotalAmount) AS TotalAmountAll FROM MatchTypeCounts GROUP BY FileManagerID) SELECT fm.BusinessFileManagerID AS FileManagerName, lk.KeyCode AS MatchTypeName, m.Count, m.TotalAmount, CAST(ROUND((m.Count * 100.0) / t.TotalRecords, 2) AS DECIMAL(6,2)) AS PercentageContribution FROM MatchTypeCounts m INNER JOIN TotalCounts t ON m.FileManagerID = t.FileManagerID INNER JOIN [Playlist].[FileManager] fm ON m.FileManagerID = fm.ID INNER JOIN [dbo].[Lookup] lk ON m.PLMatchTypeID = lk.LookupId AND lk.LookUpType = 'PL_MatchType' ORDER BY fm.BusinessFileManagerID, lk.KeyCode;" \
       -o "${REPORTS[Playlist_MatchTypes]}" -s "," -W



# -----------------------
# Loop through and upload each file
# -----------------------
for REPORT_NAME in "${!REPORTS[@]}"; do
    OUTPUT_FILE="${REPORTS[$REPORT_NAME]}"
    BLOB_PATH="$REPORTS_DIR/$REPORT_NAME/$(basename "$OUTPUT_FILE")"

    echo "[$(date)]   Uploading $REPORT_NAME → $BLOB_PATH" >> "$LOG_FILE"

    if az storage blob upload \
        --account-name "$STORAGE_ACCOUNT" \
        --container-name "$CONTAINER_NAME" \
        --name "$BLOB_PATH" \
        --file "$OUTPUT_FILE" \
        --overwrite true \
        --auth-mode login -o none; then

        echo "[$(date)] ✅ Uploaded $(basename "$OUTPUT_FILE") to '$CONTAINER_NAME/$BLOB_PATH'" >> "$LOG_FILE"
        rm -f "$OUTPUT_FILE"
    else
        echo "[$(date)] ❌ Upload failed for $OUTPUT_FILE" >> "$LOG_FILE"
    fi
done

echo "[$(date)]   All available report uploads processed." >> "$LOG_FILE"
```

### 4️⃣ Make the script executable

```bash
sudo chmod +x /home/jenkinsadmin/SQL_Report_Scripts/export_UATSQL_reports.sh
```

### 5️⃣ Run the script manually

```bash
cd /home/jenkinsadmin/SQL_Report_Scripts
./export_UATSQL_reports.sh
```

### 6️⃣ Automate using Cron

Edit crontab:

```bash
crontab -e
```

Add entries for daily execution at 7 AM:

```cron
0 7 * * * /home/jenkinsadmin/SQL_Report_Scripts/export_QASQL_reports.sh >> /home/jenkinsadmin/SQL_Report_Scripts/report_logs/cron_QASQL.log 2>&1
0 7 * * * /home/jenkinsadmin/SQL_Report_Scripts/export_UATSQL_reports.sh >> /home/jenkinsadmin/SQL_Report_Scripts/report_logs/cron_UATSQL.log 2>&1
```

```

---


Do you want me to do that?
```
