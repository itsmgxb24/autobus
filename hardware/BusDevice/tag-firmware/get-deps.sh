#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p deps

checkout() {
    target="$1"
    url="$2"
    commit="$3"
    if [ ! -d "$target/.git" ]; then
        git init -q "$target"
    fi
    if ! git -C "$target" cat-file -e "$commit^{commit}" 2>/dev/null; then
        git -C "$target" fetch -q --depth=1 "$url" "$commit"
    fi
    git -C "$target" checkout -q --detach "$commit"
}

checkout deps/TlsrComProg825x https://github.com/pvvx/TlsrComProg825x.git 2de91fe32d6a81786eabbef2ff5f82116d02176e
checkout deps/ATC_TLSR_Paper https://github.com/atc1441/ATC_TLSR_Paper.git 903fd6174e858d5fdd70c7ad66cbce14c06c29f8
chmod -R u+x deps/ATC_TLSR_Paper/Firmware/tc32_linux
