# BankNotify License Tools

## Generate a License Key

```bash
pip install -r requirements.txt

# Generate 1-year license
python3 gen_license.py customer@example.com 365

# Generate lifetime license (days=0)
python3 gen_license.py customer@example.com 0

# Use custom private key path
python3 gen_license.py customer@example.com 365 --private-key private.pem
```

## Files
- `private.pem` — RSA private key (KEEP SECRET, used to sign licenses)
- `public.pem` — RSA public key (embedded in the app for verification)
- `gen_license.py` — License key generator script

## Security Notes
- NEVER commit `private.pem` to a public repository
- The public key in `LicenseManager.kt` must match `public.pem`
- Regenerate keys for production (the bundled keys are for development only)
