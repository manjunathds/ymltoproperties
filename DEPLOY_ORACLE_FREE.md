# Deploy to Oracle Cloud Always Free

This application can run on an Oracle Cloud Infrastructure `Always Free` compute VM with Docker and automatic HTTPS.

## Recommended target

- Shape: `VM.Standard.A1.Flex`
- OCPUs: `1`
- Memory: `6 GB`
- OS: Ubuntu 24.04 or Oracle Linux 9
- Public IP: required
- Domain name: required for trusted HTTPS

This is the lowest-friction free Oracle setup for a custom Spring Boot app.

## 1. Create the OCI VM

In Oracle Cloud:

1. Open `Compute` -> `Instances` -> `Create instance`
2. Pick an `Always Free` eligible image and shape
3. Add your SSH public key
4. Keep the public IP enabled
5. Create the instance

Open these ingress ports in the subnet security list or NSG:

- `22` for SSH
- `80` for HTTP
- `443` for HTTPS

Trusted HTTPS requires a domain pointed at the VM public IP. You cannot get a normal public certificate for a bare IP address.

## 2. SSH into the VM

```bash
ssh ubuntu@YOUR_PUBLIC_IP
```

Use `opc@YOUR_PUBLIC_IP` if you chose Oracle Linux instead of Ubuntu.

## 3. Install Docker and Git

Ubuntu:

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
newgrp docker
```

Oracle Linux 9:

```bash
sudo dnf update -y
sudo dnf install -y git dnf-plugins-core
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
newgrp docker
```

## 4. Point your domain to the VM

Create a DNS `A` record before starting the stack:

```text
app.example.com -> YOUR_PUBLIC_IP
```

Wait until the hostname resolves to the VM from the public internet. Certificate issuance depends on that.

## 5. Copy the project to the VM

If the repo is already on GitHub:

```bash
git clone YOUR_REPOSITORY_URL app
cd app
```

If the repo is only local, push it to GitHub first or copy it with `scp`.

## 6. Start the application

```bash
cp deploy/oracle/.env.example deploy/oracle/.env
vim deploy/oracle/.env
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.yml up -d --build
```

Set `APP_DOMAIN` in `deploy/oracle/.env` to the hostname you pointed at the VM:

```text
APP_DOMAIN=app.example.com
```

The app will then be available at:

```text
https://app.example.com
```

## 7. Verify

```bash
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.yml ps
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.yml logs -f
curl -I https://YOUR_DOMAIN
```

To inspect only the TLS proxy:

```bash
docker logs -f yml-to-properties-caddy
```

## 8. Update after code changes

```bash
git pull
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.yml up -d --build
```

## How it works

- `Caddy` listens on ports `80` and `443`
- Caddy obtains and renews Let's Encrypt certificates automatically
- The Spring Boot app listens only on the internal Docker network at port `8080`
- `X-Forwarded-Proto` is forwarded so Spring can enforce HTTPS correctly

## Notes for this app

- `SPRING_PROFILES_ACTIVE=prod` is already configured in the Oracle compose file
- `APP_SECURITY_REQUIRE_HTTPS=true` is the default in the HTTPS env example
- If DNS is not ready yet, Caddy will keep retrying certificate provisioning until the hostname resolves correctly

## What I could not do from here

I cannot create the OCI instance, create DNS records, or run the deployment myself from this environment because it has no access to your Oracle Cloud account or domain provider.
