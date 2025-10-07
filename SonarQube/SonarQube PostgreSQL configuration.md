Perfect 👍 You want to configure **SonarQube with PostgreSQL** (the only DB officially supported for Community Edition in production).

Here’s a **step-by-step guide**:

---

## 🔹 1. Install PostgreSQL

On your SonarQube server (or a dedicated DB server):

```bash
# Install PostgreSQL (Ubuntu/Debian example)
sudo apt update
sudo apt install postgresql postgresql-contrib -y
```

Enable and start PostgreSQL:

```bash
sudo systemctl enable postgresql
sudo systemctl start postgresql
```

---

## 🔹 2. Create SonarQube Database & User

Switch to the postgres user and open psql:

```bash
sudo -u postgres psql
```

Inside `psql`, run:

```sql
-- Create a database for SonarQube
CREATE DATABASE sonarqube;

-- Create a user with a password
CREATE USER sonar WITH ENCRYPTED PASSWORD 'StrongPasswordHere';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE sonarqube TO sonar;

-- (Optional but recommended) set UTF-8 encoding
ALTER DATABASE sonarqube SET client_encoding TO 'UTF8';

\q
```

---

## Download and Extract SonarQube:
```
cd /opt
sudo wget https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-10.6.0.92116.zip
sudo apt install -y unzip
sudo unzip sonarqube-10.6.0.92116.zip
sudo mv sonarqube-10.6.0.92116 sonarqube
```

---
## 🔹 3. Configure SonarQube to Use PostgreSQL

Edit SonarQube configuration file:

```bash
cd /opt/sonarqube/conf
sudo nano sonar.properties
```

Find and update the database section (uncomment these lines and set values):

```properties
# PostgreSQL connection settings
sonar.jdbc.username=sonar
sonar.jdbc.password=StrongPasswordHere

# Replace localhost with your DB server if external
sonar.jdbc.url=jdbc:postgresql://localhost/sonarqube
```
---
## 👤 ** Create a SonarQube User**

```bash
sudo useradd -d /opt/sonarqube -s /bin/bash sonar
sudo chown -R sonar:sonar /opt/sonarqube
```

---

## 🧠 ** Create a systemd Service**

```bash
sudo nano /etc/systemd/system/sonarqube.service
```

Paste the following:

```ini
[Unit]
Description=SonarQube service
After=syslog.target network.target

[Service]
Type=forking

ExecStart=/opt/sonarqube/bin/linux-x86-64/sonar.sh start
ExecStop=/opt/sonarqube/bin/linux-x86-64/sonar.sh stop

User=sonar
Group=sonar
Restart=always
LimitNOFILE=65536
LimitNPROC=4096

[Install]
WantedBy=multi-user.target
```

---

## 🚀 **8️⃣ Start and Enable SonarQube**

```bash
sudo systemctl daemon-reload
sudo systemctl enable sonarqube
sudo systemctl start sonarqube
```

Check status:

```bash
sudo systemctl status sonarqube
```

---

## 🌐 **9️⃣ Access SonarQube**

Open your browser:

```
http://<your-server-ip>:9000
```

**Default credentials:**

```
Username: admin
Password: admin
```

You’ll be prompted to change the password on first login.

---
---

## 🔹 4. Adjust PostgreSQL Settings (optional but useful)

Open PostgreSQL config file:

```bash
sudo nano /etc/postgresql/<version>/main/pg_hba.conf
```

Add this line at the bottom (for local or network access):

```
host    sonarqube    sonar    0.0.0.0/0    md5
```

Reload PostgreSQL:

```bash
sudo systemctl restart postgresql
```

---

## 🔹 5. Start SonarQube

```bash
cd /opt/sonarqube/bin/linux-x86-64
./sonar.sh start
```

Check logs:

```bash
tail -f /opt/sonarqube/logs/sonar.log
```

If DB connection is correct, you’ll see:

```
Connected to PostgreSQL database [sonarqube]
SonarQube is up
```

---

## 🔹 6. Access SonarQube

Open browser →
👉 `http://<your-server-ip>:9000`
Default credentials:

* Username: `admin`
* Password: `admin`

---

✅ Now SonarQube is running with PostgreSQL instead of the embedded DB.
This setup supports **scaling, upgrades, and migrations**.

---

Do you want me to also give you the **steps for making SonarQube run as a systemd service** (so it auto-starts on reboot)?
 
