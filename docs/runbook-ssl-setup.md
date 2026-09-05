# Ektrepha — Nginx + SSL Setup (Reference Doc)

What's running on the backend server, for future reference.

> Note: the service name below has been corrected to `ektrepha.service` — that's the actual
> systemd unit on the box (`/etc/systemd/system/ektrepha.service`), confirmed via
> `sudo systemctl cat ektrepha.service`. The original notes this doc was written from called
> it `ektrepha-api`, which doesn't exist as a unit.

Server details:
- Instance: ektrepha-api-server (i-0f4e1cd8ae9bb995f)
- Region: us-east-1 (N. Virginia)
- Public IP: 3.82.191.236
- OS: Ubuntu 24.04
- App: Java 17 / Spring Boot, running on port 8080 (internal only, not public)
- Domain: api.ektrepha.com → points directly to 3.82.191.236 (A record in GoDaddy DNS)
- SSL: free certificate via Let's Encrypt (Certbot), auto-renews every ~60-90 days
- No load balancer used — single EC2 instance handles everything (Option B / free route)


## How traffic flows

```
Internet → https://api.ektrepha.com (port 443)
        → Nginx (reverse proxy, handles SSL)
        → http://localhost:8080 (Spring Boot app, internal only)
```

Port 8080 is NOT open to the public internet — only Nginx (running on the same machine) can
reach it via localhost. This was locked down after Certbot/SSL was confirmed working.

> This describes proxying all paths through to the app on 443 with SSL. It's a different (later?)
> state than `docs/runbook-nginx-setup.md`, which documents a port-80-only config that proxies
> just `/api/version` and 403s everything else. Run
> `sudo cat /etc/nginx/sites-available/ektrepha` on the server to see which is actually live
> before relying on either doc, and reconcile/update once confirmed.


## Nginx config file location

`/etc/nginx/sites-available/ektrepha`
(symlinked to `/etc/nginx/sites-enabled/ektrepha` to enable it)

Current config content (pre-Certbot):

```nginx
server {
    listen 80;
    server_name api.ektrepha.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Note: Certbot automatically added an HTTPS (443) server block and a redirect from HTTP → HTTPS
on top of this — the live file on the server will look slightly different (has the SSL
certificate paths added). Run `sudo cat /etc/nginx/sites-available/ektrepha` on the server to
see the current live version if needed.


## Security group rules (launch-wizard-1, sg-0b10be27a909176ab)

- HTTP, port 80, source 0.0.0.0/0 — allowed (redirects to HTTPS)
- HTTPS, port 443, source 0.0.0.0/0 — allowed (main traffic)
- SSH, port 22, source 0.0.0.0/0 — allowed (for server access; consider restricting to your IP later)
- Port 8080 — REMOVED from public access (app only reachable via Nginx/localhost now)


## Common commands for maintenance

Check if Nginx is running:
```bash
sudo systemctl status nginx
```

Restart Nginx after a config change:
```bash
sudo nginx -t          # test config first, must show "syntax is ok"
sudo systemctl restart nginx
```

Check if the Spring Boot app is running:
```bash
sudo systemctl status ektrepha.service
```

Restart the app:
```bash
sudo systemctl restart ektrepha.service
```

View app logs (if crashing or misbehaving):
```bash
journalctl -u ektrepha.service -f
```

Check SSL certificate auto-renewal is active:
```bash
sudo systemctl status certbot.timer
```
(should show "active (waiting)")

Manually test the full chain:
```bash
curl -I https://api.ektrepha.com/api/health
```
(should return 200 OK with `{"status":"UP"}`)


## Known limitations of this setup (for future reference)

- Single point of failure: if this one EC2 instance goes down, the whole backend goes down. No redundancy.
- No load balancer: can't easily run multiple instances behind this domain without switching to the Application Load Balancer route later.
- SSH open to 0.0.0.0/0: works but is broader than ideal; consider restricting to specific IPs if the team grows.
- Region is us-east-1 (N. Virginia), not ap-south-1 (Mumbai) — adds ~200ms+ latency for India-based users. Was a deliberate early decision to move faster; revisit if latency becomes a real issue.


## When to revisit this setup

- If traffic grows and you need redundancy/multiple servers → migrate to the Application Load Balancer route (target group "ektrepha" already exists in AWS, unused, ready to attach to an ALB when needed)
- If latency for India users becomes a real complaint → migrate the instance to ap-south-1 (Mumbai)
- If the team grows and needs tighter security → restrict SSH source to specific IPs, consider a bastion host or AWS Systems Manager Session Manager instead of open SSH
