#!/usr/bin/env bash
# ==============================================================
# setup-gh-runner.sh
#
# Creates a Proxmox LXC and registers it as a GitHub Actions
# self-hosted runner (label: native-builder) for this repo.
#
# Usage — run from the Proxmox node shell:
#
#   curl -fsSL https://raw.githubusercontent.com/rafaelcarlosr/discord-webhook/main/scripts/setup-gh-runner.sh \
#     | bash -s -- --token YOUR_TOKEN
#
# Get YOUR_TOKEN from:
#   GitHub → repo → Settings → Actions → Runners → New self-hosted runner
#
# Optional overrides (env vars):
#   LXC_ID=201 LXC_MEMORY=12288 LXC_CORES=6 LXC_DISK=60 LXC_BRIDGE=vmbr0
# ==============================================================
set -euo pipefail

# ── Defaults ───────────────────────────────────────────────────
REPO_URL="https://github.com/rafaelcarlosr/discord-webhook"
LXC_ID="${LXC_ID:-200}"
LXC_HOSTNAME="gh-runner"
LXC_MEMORY="${LXC_MEMORY:-10240}"   # MB  — needs ≥8 GB for GraalVM
LXC_CORES="${LXC_CORES:-4}"
LXC_DISK="${LXC_DISK:-50}"          # GB
LXC_BRIDGE="${LXC_BRIDGE:-vmbr0}"
RUNNER_LABELS="native-builder"

# ── Colours ────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}  ✓ $*${NC}"; }
info() { echo -e "${YELLOW}==> $*${NC}"; }
err()  { echo -e "${RED}ERROR: $*${NC}" >&2; exit 1; }

# ── Args ───────────────────────────────────────────────────────
TOKEN=""
while [[ $# -gt 0 ]]; do
  case $1 in
    --token) TOKEN="$2"; shift 2 ;;
    --id)    LXC_ID="$2"; shift 2 ;;
    *) err "Unknown option: $1" ;;
  esac
done

[[ -z "$TOKEN" ]] && err "--token is required.\nGet it from: $REPO_URL/settings/actions/runners/new"

# ── Pre-flight ─────────────────────────────────────────────────
command -v pct  &>/dev/null || err "'pct' not found — run this on the Proxmox node shell."
command -v pveam &>/dev/null || err "'pveam' not found — run this on the Proxmox node shell."

pct status "$LXC_ID" &>/dev/null && \
  err "LXC $LXC_ID already exists. Set a different ID with: LXC_ID=201 bash -s ..."

# ── Template ───────────────────────────────────────────────────
info "Looking for Ubuntu 24.04 template..."
TMPL_FILE=$(pveam list local 2>/dev/null | awk '/ubuntu-24\.04.*amd64/{print $1; exit}')

if [[ -z "$TMPL_FILE" ]]; then
  info "Not cached locally — downloading from Proxmox repo..."
  pveam update -q
  TMPL_NAME=$(pveam available --section system 2>/dev/null | awk '/ubuntu-24\.04.*amd64/{print $2; exit}')
  [[ -z "$TMPL_NAME" ]] && err "Could not find Ubuntu 24.04 template. Run 'pveam update' manually."
  pveam download local "$TMPL_NAME"
  TMPL_FILE="local:vztmpl/$TMPL_NAME"
else
  TMPL_FILE="local:vztmpl/$TMPL_FILE"
fi
ok "Template: $TMPL_FILE"

# ── Create LXC ─────────────────────────────────────────────────
info "Creating LXC $LXC_ID (${LXC_MEMORY} MB RAM · ${LXC_CORES} cores · ${LXC_DISK} GB)..."
pct create "$LXC_ID" "$TMPL_FILE" \
  --hostname  "$LXC_HOSTNAME" \
  --memory    "$LXC_MEMORY"   \
  --cores     "$LXC_CORES"    \
  --rootfs    "local-lvm:$LXC_DISK" \
  --net0      "name=eth0,bridge=$LXC_BRIDGE,ip=dhcp" \
  --unprivileged 0 \
  --features  nesting=1

info "Starting LXC and waiting for network..."
pct start "$LXC_ID"
sleep 10   # give it time to boot + get DHCP lease
ok "LXC started"

# ── Docker ─────────────────────────────────────────────────────
info "Installing Docker inside LXC..."
pct exec "$LXC_ID" -- bash -c "
  apt-get update -qq
  apt-get install -yq --no-install-recommends docker.io curl ca-certificates
  systemctl enable --now docker
"
ok "Docker installed"

# ── GitHub Actions runner ──────────────────────────────────────
info "Installing GitHub Actions runner..."
pct exec "$LXC_ID" -- bash -c "
  set -euo pipefail

  # Resolve latest runner release
  RUNNER_VER=\$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest \
    | grep -oP '\"tag_name\": \"v\K[^\"]+')
  RUNNER_URL=\"https://github.com/actions/runner/releases/download/v\${RUNNER_VER}/actions-runner-linux-x64-\${RUNNER_VER}.tar.gz\"

  echo \"  Downloading runner v\${RUNNER_VER}...\"
  mkdir -p /opt/actions-runner && cd /opt/actions-runner
  curl -fsSL \"\$RUNNER_URL\" | tar xz

  echo \"  Configuring...\"
  ./config.sh \
    --url      '$REPO_URL' \
    --token    '$TOKEN' \
    --name     '$LXC_HOSTNAME' \
    --labels   '$RUNNER_LABELS' \
    --work     '/opt/actions-runner/_work' \
    --unattended

  echo \"  Installing as system service...\"
  ./svc.sh install root
  ./svc.sh start
"
ok "Runner registered and running"

# ── Summary ────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  Self-hosted runner is live!${NC}"
echo ""
echo "  Check runner status:   pct exec $LXC_ID -- /opt/actions-runner/svc.sh status"
echo "  Enter container:       pct enter $LXC_ID"
echo "  GitHub runners page:   $REPO_URL/settings/actions/runners"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
