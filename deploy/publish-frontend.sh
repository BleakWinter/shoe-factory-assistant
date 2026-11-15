#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
    echo "Usage: bash publish-frontend.sh DIST RELEASE_ROOT RELEASE_ID SITE_URL" >&2
    exit 2
fi

dist=$1
release_root=$2
release_id=$3
site_url=$4

if [[ ! $release_id =~ ^[[:alnum:]][[:alnum:]_.-]*$ ]] ||
   [[ ! -s "$dist/index.html" || ! -d "$dist/assets" || -n $(find "$dist" -type l -print -quit) ]]; then
    echo "Invalid frontend build or release ID" >&2
    exit 1
fi

exec 9>"$release_root/.publish-lock"
flock -x 9

current=$(readlink "$release_root/current")
previous=$(readlink "$release_root/previous" || true)
if [[ ! $current =~ ^releases/[[:alnum:]][[:alnum:]_.-]*$ || ! -s "$release_root/$current/index.html" ]]; then
    echo "Current frontend release is missing or invalid" >&2
    exit 1
fi

release="releases/$release_id"
stage=$(mktemp -d "$release_root/releases/.staging.XXXXXX")
pointers=$(mktemp -d "$release_root/.pointers.XXXXXX")
trap 'rm -rf -- "$stage" "$pointers"' EXIT

cp -R "$dist/." "$stage/"
chmod -R a+rX "$stage"
mv -T "$stage" "$release_root/$release"

set_pointer() {
    ln -s "$2" "$pointers/$1"
    mv -Tf "$pointers/$1" "$release_root/$1"
}

set_pointer previous "$current"
set_pointer current "$release"

if ! curl --noproxy '*' -fsS --retry 2 --max-time 8 "$site_url" | cmp -s "$release_root/$release/index.html"; then
    set_pointer current "$current"
    if [[ -n $previous ]]; then
        set_pointer previous "$previous"
    else
        rm -f "$release_root/previous"
    fi
    echo "Frontend verification failed. Restored the preceding release." >&2
    exit 1
fi

echo "Published frontend release $release_id"
