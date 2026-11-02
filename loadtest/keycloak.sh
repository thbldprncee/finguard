# Keycloak helpers for the test tooling (loadtest/run.command, ml/run.sh). Source it after
# setting KC (admin API base URL), KEYCLOAK_ADMIN_PASSWORD, CLIENT_ID and a fail() function.

admin_token() {
  curl -s --max-time 10 -X POST "$KC/realms/master/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=admin-cli -d username=admin \
    --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
    | sed -nE 's/.*"access_token":"([^"]+)".*/\1/p'
}

kc_api() { # method path [json]
  if [ $# -ge 3 ]; then
    curl -s --max-time 10 -o /dev/null -w '%{http_code}' -X "$1" "$KC/admin/realms/finguard$2" \
      -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$3"
  else
    curl -s --max-time 10 -X "$1" "$KC/admin/realms/finguard$2" -H "Authorization: Bearer $TOKEN"
  fi
}

# Public client with password login for the test only (the app's own client keeps it
# disabled); same audience mapper as the frontend client.
create_client() {
  TOKEN="$(admin_token)"
  [ -n "$TOKEN" ] || fail "cannot log in to the Keycloak admin API (check KEYCLOAK_ADMIN_PASSWORD)"
  local code
  code=$(kc_api POST /clients '{
    "clientId": "'"$CLIENT_ID"'", "enabled": true, "publicClient": true,
    "directAccessGrantsEnabled": true, "standardFlowEnabled": false, "protocol": "openid-connect",
    "protocolMappers": [{
      "name": "finguard-api audience", "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
      "config": {"included.custom.audience": "finguard-api", "access.token.claim": "true", "id.token.claim": "false"}
    }]}')
  case "$code" in 201|409) ;; *) fail "creating client failed: HTTP $code" ;; esac
}

delete_client() {
  TOKEN="$(admin_token)"
  cid=$(kc_api GET "/clients?clientId=$CLIENT_ID" | sed -nE 's/^\[\{"id":"([^"]+)".*/\1/p')
  [ -n "$cid" ] && kc_api DELETE "/clients/$cid" >/dev/null && echo "temporary client $CLIENT_ID deleted"
}

# Creates <prefix>-0001@loadtest.local ... with the password kept in loadtest/.loadtest-password
# (created on first use) and sets LOADTEST_PASSWORD.
ensure_users() { # prefix count
  local prefix=$1 count=$2 pw_file="loadtest/.loadtest-password" fresh=0 created=0 existing=0 i u code id
  if [ -f "$pw_file" ]; then
    LOADTEST_PASSWORD="$(cat "$pw_file")"
  else
    LOADTEST_PASSWORD="Lt$(openssl rand -hex 12)"; echo "$LOADTEST_PASSWORD" > "$pw_file"; chmod 600 "$pw_file"; fresh=1
  fi
  TOKEN="$(admin_token)"
  for i in $(seq 1 "$count"); do
    [ $((i % 40)) = 0 ] && TOKEN="$(admin_token)"   # admin tokens live 60 s
    u="$prefix-$(printf '%04d' "$i")@loadtest.local"
    code=$(kc_api POST /users '{
      "username": "'"$u"'", "email": "'"$u"'", "emailVerified": true, "enabled": true,
      "firstName": "Load", "lastName": "Test '"$i"'", "requiredActions": [],
      "credentials": [{"type": "password", "value": "'"$LOADTEST_PASSWORD"'", "temporary": false}]}')
    if [ "$code" = "201" ]; then
      created=$((created + 1))
    elif [ "$code" = "409" ]; then
      existing=$((existing + 1))
      if [ "$fresh" = "1" ]; then # user from an older run whose password file was lost
        id=$(kc_api GET "/users?username=$u&exact=true" | sed -nE 's/^\[\{"id":"([^"]+)".*/\1/p')
        [ -n "$id" ] && kc_api PUT "/users/$id/reset-password" \
          '{"type": "password", "value": "'"$LOADTEST_PASSWORD"'", "temporary": false}' >/dev/null
      fi
    else
      fail "creating user $u failed: HTTP $code"
    fi
  done
  echo "users: $created created, $existing already existed"
}

# Keycloak ids of <prefix>-* users, one per line, into <file>.
user_ids() { # prefix count file
  TOKEN="$(admin_token)"
  kc_api GET "/users?search=$1-&max=$(($2 + 1000))&briefRepresentation=true" \
    | grep -oE '"id":"[0-9a-f-]{36}","username":"'"$1"'-[0-9]+@loadtest\.local"' \
    | cut -d'"' -f4 > "$3"
  [ "$(wc -l < "$3" | tr -d ' ')" -ge "$2" ] || fail "found only $(wc -l < "$3" | tr -d ' ') $1 user ids"
}
