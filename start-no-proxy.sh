#!/bin/bash
# Start Spring Boot app with proxy bypass for email servers

# Add email servers to no_proxy
export NO_PROXY="localhost,127.0.0.1,169.254.169.254,metadata.google.internal,*.svc.cluster.local,*.local,*.googleapis.com,*.google.com,outlook.office365.com,smtp-mail.outlook.com,imap.gmail.com,smtp.gmail.com,imap.mail.yahoo.com,smtp.mail.yahoo.com"
export no_proxy="$NO_PROXY"

# Also set Java system properties to disable proxy for these hosts
export JAVA_OPTS="-Dhttp.nonProxyHosts=localhost|127.0.0.1|*.local|*.google.com|*.googleapis.com|outlook.office365.com|smtp-mail.outlook.com|imap.gmail.com|smtp.gmail.com|imap.mail.yahoo.com|smtp.mail.yahoo.com"

echo "=========================================="
echo "Starting AI Email Chat (No Proxy Mode)"
echo "=========================================="
echo ""
echo "Email servers will bypass the proxy:"
echo "  - outlook.office365.com"
echo "  - smtp-mail.outlook.com"
echo "  - imap.gmail.com"
echo "  - smtp.gmail.com"
echo "  - imap.mail.yahoo.com"
echo "  - smtp.mail.yahoo.com"
echo ""
echo "=========================================="
echo ""

cd /home/user/ai-email-chat
./gradlew bootRun
