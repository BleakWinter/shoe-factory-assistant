# Homeserver monitoring

Prometheus stores host, Docker container, and Jenkins cgroup metrics. Grafana is
served through the existing Tailscale-only Nginx at
`http://100.107.45.39/grafana/`. Alertmanager sends firing and recovery emails to
the existing operator mailbox over QQ SMTP with verified implicit TLS.

## Runtime layout

Install this directory as `/srv/shoe-factory-monitoring`. Keep `private/` and
`.env` on the server; neither belongs in Git. The Grafana admin password is in
`private/grafana-admin-password`. Retrieve it over the existing root SSH
connection; never put it in Notion or a deployment log.

All collectors, Prometheus, and Alertmanager listen on loopback. Grafana listens
only on Docker's host bridge, so the existing Nginx can reach it without exposing
new ports on the LAN or Tailscale interface. cAdvisor has host access to read
Docker/cgroup metrics. It does not export application environment variables or
arbitrary Docker labels.

The provisioned overview distinguishes available memory, file cache, working set,
and anonymous memory. Jenkins is read from `/system.slice/jenkins.service`, which
also accounts for build processes that remain in that service's cgroup. This
setup does not change Jenkins, JVM, or application container resource limits.

## Install and validate

On Debian, install `python3-yaml`, then run `python3 prepare-secrets.py` as root.
The script reads only the existing production configuration, reuses its SMTP
account, and refuses a recipient different from the mailbox owner. It generates
a private random Grafana admin password on first use. It does not print secrets.

Pull the official versioned images in `compose.yml`. The server's `.env` may use
the existing DaoCloud mirror; verify downloaded digests against the upstream
registry, then pin each image to the verified digest in that file. When images
are imported offline, `.env` pins their verified immutable local image IDs and
`verified-images.json` records the upstream manifest digests and source versions.

Before starting:

```sh
docker compose config --quiet
docker compose run --rm --no-deps --entrypoint /bin/promtool prometheus check config /etc/prometheus/prometheus.yml
docker compose run --rm --no-deps --entrypoint /bin/amtool alertmanager check-config /etc/alertmanager/alertmanager.yml
docker compose run --rm --no-deps --entrypoint /bin/promtool -v "$PWD:/work:ro" -w /work prometheus test rules rules.test.yml
```

Start with `docker compose up -d`, add the locations in `nginx-grafana.conf` to
the existing Nginx server block, validate with `docker exec nginx nginx -t`, then
reload Nginx. Use a file-preserving write when changing a configuration file
that is already bind-mounted into Nginx.

Install `shoe-factory-monitoring.service` in `/etc/systemd/system/`, run
`systemctl daemon-reload`, then `systemctl enable --now shoe-factory-monitoring`.
This waits for Docker and the Tailscale address before reconciling the monitoring
stack and starting the existing Nginx container. It repairs the observed boot
race where Docker starts Nginx before `100.107.45.39` exists. It does not reboot
the server as part of installation.

Verify all Prometheus targets, host temperature and RAPL readings, five business
containers, Jenkins cgroup CPU/memory, dashboard login, and business endpoints.
Send a clearly labelled synthetic test alert through a temporary Prometheus rule,
confirm Alertmanager's successful SMTP notification counter, resolve the alert,
and remove its rule. The synthetic rule does not create real host resource load.
Only remove Netdata after this replacement has passed validation.

## Default alert policy

| Condition | Required duration |
| --- | --- |
| Host CPU >90% | 10 minutes |
| Available memory <10% / <5% | 5 / 2 minutes |
| CPU temperature >85°C | 3 minutes |
| `/` or `/backup` free space <15% | 15 minutes |
| Swap >25% with sustained paging | 5 minutes |
| Container working set >90% of limit | 5 minutes |
| Nacos working set >97% of limit | 10 minutes |
| Container or Jenkins CPU >4 logical cores | 10 minutes |
| Jenkins anonymous memory >3GiB | 5 minutes |
| Core system service or scrape target down | 2 minutes |
| Business container absent | 3-minute absence window plus 2-minute hold |
| Container OOM event | At the next evaluation |

Alerts are grouped, repeated at most every four hours while unchanged, and send
a recovery email. The 3GiB Jenkins value is an alert threshold, not a hard limit.
The critical memory alert inhibits the matching warning to avoid duplicate mail.
Thresholds are initial operational defaults and can be changed in `rules/`.

## Data and operation

Prometheus retains 14 days or 5GB, whichever limit is reached first. Grafana,
Prometheus, and Alertmanager use named Docker volumes. Container logs rotate at
three 5MB files. Do not use `docker compose down -v` unless deleting monitoring
history is intended.

After editing rules, run the rule tests and configuration check, then POST to
`http://127.0.0.1:9090/-/reload`. After changing email configuration, run `amtool`
and POST to `http://127.0.0.1:9093/-/reload`. These endpoints remain loopback-only.

If restoring the old Nginx configuration, validate and reload it before stopping
Grafana. Existing business containers are independent of this Compose project.
