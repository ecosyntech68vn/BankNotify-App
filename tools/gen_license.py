#!/usr/bin/env python3
"""
BankNotify License Key Generator

Usage:
  python3 gen_license.py <email> <days> [--private-key private.pem]

Example:
  python3 gen_license.py customer@example.com 365
  python3 gen_license.py customer@example.com 0 --days 0 means lifetime
"""

import argparse
import base64
import time
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.backends import default_backend


def main():
    parser = argparse.ArgumentParser(description="Generate BankNotify license key")
    parser.add_argument("email", help="Customer email address")
    parser.add_argument("days", type=int, help="License validity in days (0 = lifetime)")
    parser.add_argument("--private-key", default="private.pem", help="Path to RSA private key PEM")
    args = parser.parse_args()

    with open(args.private_key, "rb") as f:
        private_key = serialization.load_pem_private_key(f.read(), password=None, backend=default_backend())

    # Calculate expiry: 0 days = 100 years from now (effectively lifetime)
    if args.days == 0:
        expiry = int(time.time() * 1000) + 100 * 365 * 86400 * 1000
    else:
        expiry = int(time.time() * 1000) + args.days * 86400 * 1000

    data = f"{args.email}|{expiry}"
    data_bytes = data.encode("utf-8")

    signature = private_key.sign(data_bytes, padding.PKCS1v15(), hashes.SHA256())

    data_b64 = base64.urlsafe_b64encode(data_bytes).rstrip(b"=").decode("ascii")
    sig_b64 = base64.urlsafe_b64encode(signature).rstrip(b"=").decode("ascii")

    license_key = f"{data_b64}.{sig_b64}"

    print(f"License Key:  {license_key}")
    print(f"Email:        {args.email}")
    print(f"Expires:      {args.days} days ({time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime(expiry / 1000))})")
    print(f"Key length:   {len(license_key)} chars")


if __name__ == "__main__":
    main()
