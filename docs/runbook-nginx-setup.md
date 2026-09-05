# Runbook: nginx reverse proxy setup (prod EC2)

Context: `/api/version` was reported as "not working." Investigation found the app itself
was healthy — the confusion was that `logDeployedVersion()` (`EktrephaApplication.java`)
is a startup log line (`@EventListener(ApplicationReadyEvent.class)`), not an HTTP API.
The real endpoint is `GET /api/version` (`VersionController.java`), which was working fine.

The actual issue found during the investigation: the EC2 security group had **all** inbound
ports open to `0.0.0.0/0` (80, 8080, 443, 22), including the app's port 8080 directly. Logs
showed internet scanners hitting Tomcat directly (GPON exploit probes, raw TLS handshakes
sent to the plain-HTTP port). Fix: put nginx in front on port 80, expose only `/api/version`,
and close 8080 at the security-group level.

Server: `ubuntu@3.82.191.236` (instance backing `ektrepha.service`)

## 1. Health checks used during triage

```bash
# zombie processes
ps -eo pid,ppid,stat,cmd | awk '$3 ~ /Z/'
ps -eo pid,ppid,stat,cmd | awk '$3 ~ /Z/ {print $2}' | sort -u | xargs -r -I{} ps -p {} -o pid,cmd

# app/service status
sudo systemctl status ektrepha.service
sudo journalctl -u ektrepha.service -n 80 --no-pager
ps aux | grep '[j]ava'

# what's actually listening
sudo ss -tlnp

# app-level check
curl -sS -i http://localhost:8080/api/version
```

Finding: app was healthy (clean Liquibase/Hibernate/Tomcat boot, correct
`Deployed version=... builtAt=...` log line, `/api/version` returned 200). The 5 zombie
processes found were defunct `less` procs from manual `journalctl | less` sessions —
harmless, unrelated to the app.

## 2. Install nginx

```bash
sudo apt-get update -y
sudo apt-get install -y nginx
sudo systemctl enable nginx
sudo systemctl start nginx
```

Verify:
```bash
nginx -v
sudo systemctl status nginx
```

## 3. Reverse-proxy config — expose only specific endpoints

`/etc/nginx/sites-available/ektrepha`:
```nginx
server {
    listen 80 default_server;
    server_name _;

    location = /api/version {
        proxy_pass http://127.0.0.1:8080/api/version;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        return 403;
    }
}
```

Enable the site and remove the default placeholder:
```bash
sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/ektrepha /etc/nginx/sites-enabled/ektrepha
sudo nginx -t
sudo systemctl restart nginx
```

Verify:
```bash
curl -sS -i http://localhost/api/version     # expect 200
curl -sS -i http://localhost/actuator/health # expect 403 (not exposed)
```

To expose additional endpoints later, add more `location = /path { proxy_pass ...; }`
blocks above the catch-all `location / { return 403; }`.

## 4. Security group changes (AWS console — EC2 → Security Groups → Edit inbound rules)

Original state: all 4 rules (HTTP 80, Custom TCP 8080, HTTPS 443, SSH 22) had source
`0.0.0.0/0`.

- [ ] **Delete the port 8080 rule** — nginx on port 80 is the only intended public path in
      once it's configured and verified.
- [ ] **Restrict SSH (22)** from `0.0.0.0/0` to a known IP, e.g. `<your-ip>/32`. Test in a
      **new terminal** before closing the current session, in case the IP is wrong/dynamic.
- Keep port 80 open to `0.0.0.0/0` (needed for public API access via nginx).
- Port 443: only needed if/when TLS is set up (see below).

## 5. Optional next step: TLS

Once a domain points at the server:
```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d yourdomain.com
```

## 6. Troubleshooting reference

```bash
sudo journalctl -u nginx -n 50 --no-pager
sudo tail -f /var/log/nginx/error.log
sudo tail -f /var/log/nginx/access.log
```

## Separate, unrelated finding (local repo, not yet resolved)

`src/main/java/com/ektrepha/EktrephaApplication.java` had prod DB credentials
(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and a `PGPASSWORD=... psql` command) pasted
directly into the class body as invalid Java — uncommitted, working-tree only. Needs to be
removed from source; if real credentials were ever committed/pushed anywhere, rotate them.
