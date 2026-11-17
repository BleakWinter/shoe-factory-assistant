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
find "$release_root/releases" -mindepth 1 -maxdepth 1 -type d -name '.staging.??????' -exec rm -rf -- {} +
find "$release_root" -mindepth 1 -maxdepth 1 -type d -name '.pointers.??????' -exec rm -rf -- {} +

current=$(readlink "$release_root/current")
previous=$(readlink "$release_root/previous" || true)
if [[ ! $current =~ ^releases/[[:alnum:]][[:alnum:]_.-]*$ || ! -s "$release_root/$current/index.html" ]]; then
    echo "Current frontend release is missing or invalid" >&2
    exit 1
fi
if [[ -n $previous && ! $previous =~ ^releases/[[:alnum:]][[:alnum:]_.-]*$ ]]; then
    echo "Previous frontend release pointer is invalid" >&2
    exit 1
fi

release="releases/$release_id"
if [[ -e "$release_root/$release" || -L "$release_root/$release" ]]; then
    echo "Frontend release already exists: $release_id" >&2
    exit 1
fi
stage=$(mktemp -d "$release_root/releases/.staging.XXXXXX")
pointers=''
trap 'rm -rf -- "$stage" "$pointers"' EXIT
pointers=$(mktemp -d "$release_root/.pointers.XXXXXX")

cp -R "$dist/." "$stage/"
chmod -R a+rX "$stage"
mv -T "$stage" "$release_root/$release"

set_pointer() {
    ln -sfn "$2" "$pointers/$1" && mv -Tf "$pointers/$1" "$release_root/$1"
}

verify_release() {
    curl --noproxy '*' -fsS --retry 2 --max-time 8 "$site_url" | cmp -s "$release_root/$1/index.html"
}

if ! { set_pointer previous "$current" && set_pointer current "$release" && verify_release "$release"; }; then
    set_pointer current "$current"
    if [[ -n $previous ]]; then
        set_pointer previous "$previous"
    else
        rm -f "$release_root/previous"
    fi
    rm -rf -- "$release_root/$release"
    echo "Frontend verification failed. Restored the preceding release." >&2
    exit 1
fi

# The previous directory still serves assets requested by already-open pages.
for directory in "$release_root"/releases/*; do
    [[ -d $directory && ! -L $directory ]] || continue
    name=${directory##*/}
    [[ $name == baseline || $name =~ ^[0-9a-f]{12}-[0-9]+$ ]] || continue
    [[ releases/$name != "$release" && releases/$name != "$current" ]] || continue
    rm -rf -- "$directory"
done

echo "Published frontend release $release_id"
