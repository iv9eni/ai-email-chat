#!/bin/bash
# Simple Outlook IMAP authentication test
# This helps diagnose whether the issue is credentials or configuration

echo "=== Outlook IMAP Authentication Test ==="
echo ""
echo "This will test IMAP connection to Outlook/Hotmail"
echo ""
read -p "Enter your Hotmail email address: " EMAIL
read -p "Enter your app password (without dashes): " -s PASSWORD
echo ""
echo ""
echo "Testing connection to outlook.office365.com:993..."
echo ""

# Use OpenSSL to test IMAP connection
(
  sleep 1
  echo "a001 LOGIN $EMAIL $PASSWORD"
  sleep 1
  echo "a002 LOGOUT"
  sleep 1
) | openssl s_client -connect outlook.office365.com:993 -crlf -quiet 2>&1 | grep -A 5 "a001"

echo ""
echo "If you see 'a001 OK' above, authentication succeeded!"
echo "If you see 'a001 NO' or 'AUTHENTICATE failed', the credentials are wrong."
