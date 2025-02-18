# Simple Webserver

- **Accepts and parse HTTP/1.1 requests** (GET, HEAD, PUT, DELETE).
- **Serves static files** (returns 200 or 404).
- **Returns correct HTTP status codes** (400, 405, 401, 403, etc.).
- **Basic authentication** via `.password` files (401 if no credentials, 403 if invalid, 200 if valid).
- **Threaded request handling** using a fixed thread pool.
- **PUT** creates/overwrites files, **DELETE** removes them, with correct status responses.

**Feel free to clone the repository and run tests on your own**

🛠 Setup & Running

1️⃣ Compile the Server
javac -d . server/config/MimeTypes.java server/WebServer.java

2️⃣ Start the Server
java server.WebServer 9999 /Users/yourusername/Desktop/web-server-root

3️⃣Open New Terminal

🧪 Testing the Server
📂 1. Basic File Serving (GET)

Create test file:
echo "Hello, Web Server!" > /Users/yourusername/Desktop/web-server-root/hello.txt

Request the file:
curl -v http://localhost:9999/hello.txt

✅ Expected Response
HTTP/1.1 200 OK
Content-Type: text/plain
Content-Length: 18

Hello, Web Server!

Test a non-existent file:
curl -v http://localhost:9999/missing.txt

✅ Expected Response
HTTP/1.1 404 Not Found

📝 2. Test HEAD Requests
curl -I http://localhost:9999/hello.txt

✅ Expected Response (no body)
HTTP/1.1 200 OK
Content-Type: text/plain
Content-Length: 18

📄 3. Test PUT Request (Create/Update a File)
curl -X PUT -d "This is a new file." http://localhost:9999/newfile.txt

✅ Expected Response
HTTP/1.1 201 Created

🗑 4. Test DELETE Request
curl -X DELETE http://localhost:9999/newfile.txt

✅ Expected Response
HTTP/1.1 204 No Content

🚫 5. Test Invalid Requests
Unsupported HTTP Method
curl -X POST http://localhost:9999/

✅ Expected Response
HTTP/1.1 405 Method Not Allowed

Malformed Request
nc localhost 9999 <<EOF
INVALID / HTTP/1.1
EOF

✅ Expected Response
HTTP/1.1 400 Bad Request

🔐 6. Test Authentication (401 Unauthorized, 403 Forbidden)
Set Up Authentication
echo "admin:password123" > /Users/yourusername/Desktop/web-server-root/.password

Request file without credentials (Expect 401 Unauthorized)
curl -v http://localhost:9999/hello.txt

✅ Expected Response
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Basic realm="667 Server"

Send Correct Credentials (200 OK)
curl -v -u admin:password123 http://localhost:9999/hello.txt

✅ Expected Response
HTTP/1.1 200 OK

Send Incorrect Credentials (403 Forbidden)
curl -v -u wronguser:wrongpass http://localhost:9999/hello.txt

✅ Expected Response
HTTP/1.1 403 Forbidden

⚡ 7. Test Multithreading
Run multiple requests in parallel:
for i in {1..10}; do curl -s http://localhost:9999/hello.txt & done
wait

✅ The server should handle all requests simultaneously without errors.

🛑 8. Stop the Server
Press Ctrl + C in the terminal where the server is running.

📌 Summary of Tests
✅ File Serving (GET, HEAD) – Returns 200 or 404.
✅ File Modification (PUT, DELETE) – Returns 201, 204, or 500.
✅ Invalid Requests – Correctly returns 400 or 405.
✅ Authentication – Handles 401, 403, and 200.
✅ Multithreading – Processes multiple requests concurrently.
