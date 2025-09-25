
Migration steps (keeping old logs intact)

1. **Stop Kafka**
   ```bash
   sudo systemctl stop kafka
   ```
2. **Create new log directory**

   ```bash
   sudo mkdir -p /var/lib/kafka-logs
   sudo chown -R adminuser:adminuser /var/lib/kafka-logs
   sudo chmod 700 /var/lib/kafka-logs
   ```

3. **Migrate existing data from `/tmp/kafka-logs`**

   ```bash
   sudo rsync -av /tmp/kafka-logs/ /var/lib/kafka-logs/
   ```
 `rsync` will copy all partitions, offsets, checkpoints, etc., and preserve permissions/timestamps.

4. **Update Kafka config**
   Edit `/opt/kafka/config/server.properties`
   Find the line:

   ```properties
   log.dirs=/tmp/kafka-logs
   ```

   Change it to:

   ```properties
   log.dirs=/var/lib/kafka-logs
   ```

5. **(Optional) Backup old dir instead of deleting immediately**

   ```bash
   sudo mv /tmp/kafka-logs /tmp/kafka-logs.bak
   ```

6. **Start Kafka**

   ```bash
   sudo systemctl daemon-reload
   sudo systemctl start kafka
   sudo systemctl status kafka
   ```

---
###  After migration

* Kafka will now read/write from `/var/lib/kafka-logs`.
* All your **existing topics and offsets** will remain intact because we migrated them.
* Once you confirm Kafka is healthy, you can delete `/tmp/kafka-logs.bak`.

---

