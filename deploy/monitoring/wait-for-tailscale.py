#!/usr/bin/env python3
"""Wait until the address used by the existing Nginx port binding is available."""
import argparse
import errno
import socket
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("address")
    parser.add_argument("--timeout", type=float, default=60)
    args = parser.parse_args()
    deadline = time.monotonic() + args.timeout
    while True:
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as connection:
                connection.bind((args.address, 0))
            print("Tailscale address ready:", args.address, flush=True)
            return
        except OSError as error:
            if error.errno != errno.EADDRNOTAVAIL:
                raise
            if time.monotonic() >= deadline:
                raise SystemExit("Tailscale address did not become ready before timeout")
            time.sleep(1)


if __name__ == "__main__":
    main()
