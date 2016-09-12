#!/usr/bin/env bash
curl -X PUT localhost:8080/rest/1.0/hg/event/$1/event -v  -i -H "serviceSharedSecret: $SERVICE_SHARED_SECRET" --data-binary @test_game_event.json