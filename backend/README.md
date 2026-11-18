# CEREVIX AI Backend Gateway & Razorpay Billing Service

## 1. Local Setup & Testing
```bash
cd backend
cp .env.example .env
# Fill Razorpay TEST mode keys (rzp_test_...) and GEMINI_API_KEY in .env
npm test
npm start
```

## 2. Automated Tests
Run the full backend & billing verification suite:
```bash
node --test tests/cerevix_backend.test.js
```

## 3. Google Cloud Run Production Deployment
1. Store secrets in **Google Cloud Secret Manager** (never in source code or Firestore):
   - `GEMINI_API_KEY`
   - `RAZORPAY_KEY_ID`
   - `RAZORPAY_KEY_SECRET`
   - `RAZORPAY_WEBHOOK_SECRET`
   - `CEREVIX_API_SECRET`
   - `FIREBASE_SERVICE_ACCOUNT`
2. Build & deploy container:
   ```bash
   gcloud builds submit --tag asia-south1-docker.pkg.dev/gen-lang-client-0458088105/cerevix/cerevix-ai-api:v1 .
   gcloud run deploy cerevix-ai-api \
     --image asia-south1-docker.pkg.dev/gen-lang-client-0458088105/cerevix/cerevix-ai-api:v1 \
     --region asia-south1 \
     --allow-unauthenticated \
     --set-secrets="GEMINI_API_KEY=GEMINI_API_KEY:latest,RAZORPAY_KEY_ID=RAZORPAY_KEY_ID:latest,RAZORPAY_KEY_SECRET=RAZORPAY_KEY_SECRET:latest,RAZORPAY_WEBHOOK_SECRET=RAZORPAY_WEBHOOK_SECRET:latest,CEREVIX_API_SECRET=CEREVIX_API_SECRET:latest,FIREBASE_SERVICE_ACCOUNT=FIREBASE_SERVICE_ACCOUNT:latest"
   ```
3. Map custom domain `api.cerevix.ai` in Cloud Run Domain Mappings (provisions managed TLS/HTTPS certificate) and point DNS `CNAME`/`A` records as instructed by Cloud Run.
4. Configure Razorpay Webhook URL:
   - URL: `https://api.cerevix.ai/v1/webhooks/razorpay`
   - Events: `subscription.authenticated`, `subscription.activated`, `subscription.charged`, `subscription.pending`, `subscription.halted`, `subscription.cancelled`, `payment.captured`, `payment.failed`, `refund.processed`.
   - Set the exact webhook secret in `RAZORPAY_WEBHOOK_SECRET`.
5. Verify `/health` endpoint returns `"status": "ok"`, `"razorpayConfigured": true`, and `"webhookSecretConfigured": true`.
