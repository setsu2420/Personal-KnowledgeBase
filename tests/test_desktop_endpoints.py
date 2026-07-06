import sys
import json
import time
import urllib.request
import urllib.error

API_BASE = "http://localhost:8080/api"

def print_step(msg):
    print(f"\n[STEP] {msg}")

def print_ok(msg):
    print(f"  ✓ SUCCESS: {msg}")

def print_fail(msg):
    print(f"  ✗ FAILED: {msg}")
    sys.exit(1)

def http_get(url):
    req = urllib.request.Request(url)
    try:
      with urllib.request.urlopen(req, timeout=10) as response:
          return json.loads(response.read().decode('utf-8'))
    except urllib.error.URLError as e:
        raise e

def test_endpoints():
    print("====================================================")
    print("  DESKTOP ENDPOINTS VERIFICATION TEST")
    print("====================================================")
    
    # 1. Wait for server to be ready
    print_step("1. Waiting for Spring Boot backend to become ready...")
    ready = False
    for i in range(30):
        try:
            health = http_get(f"{API_BASE}/health")
            if health.get("code") == 200:
                ready = True
                print_ok("Backend server detected and responsive.")
                break
        except Exception:
            pass
        time.sleep(1)
        
    if not ready:
        print_fail("Spring Boot backend did not start within 30 seconds.")

    # 2. Test /api/health details
    print_step("2. Retrieving health statistics from /api/health...")
    try:
        health_res = http_get(f"{API_BASE}/health")
        assert health_res.get("code") == 200, "Expected API response code 200"
        
        data = health_res.get("data", {})
        status = data.get("status")
        version = data.get("version")
        database = data.get("database")
        jvm = data.get("jvm", {})
        
        print(f"  - Status: {status}")
        print(f"  - Version: {version}")
        print(f"  - Database Status: {database}")
        print(f"  - JVM Uptime: {jvm.get('uptimeMs')} ms")
        print(f"  - JVM Memory Usage: Used={jvm.get('totalMemoryBytes') - jvm.get('freeMemoryBytes')} bytes, Max={jvm.get('maxMemoryBytes')} bytes")
        
        assert status == "UP", f"Expected status 'UP', got '{status}'"
        assert version == "1.0.0", f"Expected version '1.0.0', got '{version}'"
        assert database == "CONNECTED", f"Expected database 'CONNECTED', got '{database}'"
        assert jvm.get("maxMemoryBytes") > 0, "Max memory should be positive"
        
        print_ok("Health statistics verification passed successfully.")
    except Exception as e:
        print_fail(f"Health endpoint check failed: {e}")

    # 3. Test /api/admin/logs
    print_step("3. Fetching backend application logs from /api/admin/logs...")
    try:
        logs_res = http_get(f"{API_BASE}/admin/logs?limit=10")
        assert logs_res.get("code") == 200, "Expected API response code 200"
        
        log_lines = logs_res.get("data", [])
        print(f"  - Retrieved {len(log_lines)} log lines:")
        for idx, line in enumerate(log_lines):
            print(f"    [{idx+1}] {line}")
            
        assert len(log_lines) > 0, "Log lines list should not be empty"
        print_ok("Backend logs retrieval verified successfully.")
    except Exception as e:
        print_fail(f"Logs endpoint check failed: {e}")

    print("\n====================================================")
    print("🎉 ALL DESKTOP ENDPOINT TESTS PASSED SUCCESSFULLY!")
    print("====================================================")

if __name__ == "__main__":
    test_endpoints()
