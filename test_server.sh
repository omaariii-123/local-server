#!/bin/bash

# Configuration (Change port if needed based on your config.json)
BASE_URL="http://localhost:8080"
TEST_FILE="test_upload.txt"

echo "Creating dummy file for upload tests..."
echo "This is a test file for multipart and chunked uploads." > $TEST_FILE

echo -e "\n========================================"
echo "🧪 TEST 1: Basic GET & Directory Listing"
echo "========================================"
curl -i -X GET "$BASE_URL/"

echo -e "\n\n========================================"
echo "🧪 TEST 2: Virtual Host Routing"
echo "========================================"
# Spoofs the Host header to test your Router's findMatchedServer logic
curl -i -X GET "$BASE_URL/" -H "Host: custom.local"

echo -e "\n\n========================================"
echo "🧪 TEST 3: Session & Cookies (1st Request - Should Issue Cookie)"
echo "========================================"
# -c saves the cookie to a file
curl -i -c cookie.txt -X GET "$BASE_URL/"

echo -e "\n\n========================================"
echo "🧪 TEST 4: Session & Cookies (2nd Request - Should Send Cookie)"
echo "========================================"
# -b sends the cookie back to the server
curl -i -b cookie.txt -X GET "$BASE_URL/"
rm cookie.txt

echo -e "\n\n========================================"
echo "🧪 TEST 5: Multipart Form Upload"
echo "========================================"
# -F forces a multipart/form-data payload with a boundary
curl -i -X POST "$BASE_URL/upload" -F "file=@$TEST_FILE"

echo -e "\n\n========================================"
echo "🧪 TEST 6: Chunked Transfer Encoding"
echo "========================================"
# Piping data into curl forces it to use Transfer-Encoding: chunked instead of Content-Length
cat $TEST_FILE | curl -i -X POST "$BASE_URL/upload" -H "Transfer-Encoding: chunked" --data-binary @-

echo -e "\n\n========================================"
echo "🧪 TEST 7: 404 Error Handling"
echo "========================================"
curl -i -X GET "$BASE_URL/this_path_does_not_exist"

# Cleanup
rm $TEST_FILE
echo -e "\n✅ Tests Completed."