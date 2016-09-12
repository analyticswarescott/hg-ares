#!/usr/bin/env bash
curl -X POST localhost:8080/rest/1.0/ares/tenants -v  -i -H "serviceSharedSecret: $SERVICE_SHARED_SECRET" --data-binary @tenant.json