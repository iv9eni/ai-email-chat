#!/bin/bash
# Initialize Ollama with a model after containers are up

echo "=========================================="
echo "Initializing Ollama with AI Model"
echo "=========================================="
echo ""

# Wait for Ollama to be ready
echo "Waiting for Ollama service to be ready..."
until curl -s http://localhost:11434/api/tags > /dev/null 2>&1; do
    echo -n "."
    sleep 2
done
echo ""
echo "✓ Ollama is ready!"
echo ""

# Check if model already exists
if curl -s http://localhost:11434/api/tags | grep -q "llama2"; then
    echo "✓ Model 'llama2' already exists"
else
    echo "Pulling Ollama model (this may take a few minutes)..."
    docker exec ai-email-ollama ollama pull llama2
    echo ""
    echo "✓ Model 'llama2' pulled successfully!"
fi

echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Your AI Email Chat is ready!"
echo ""
echo "Web Interface: http://localhost:8080"
echo "Add accounts at: http://localhost:8080/accounts"
echo ""
echo "Next steps:"
echo "1. Open http://localhost:8080/accounts"
echo "2. Click 'Add Account'"
echo "3. Select your email provider (Gmail, Outlook, Yahoo)"
echo "4. Fill in your credentials (use app password)"
echo "5. Send test email with subject: [AI_REQUEST] Hello AI"
echo ""
