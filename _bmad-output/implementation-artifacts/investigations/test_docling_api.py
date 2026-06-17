import urllib.request
import base64
import json

# 读取测试文件并 Base64 编码
with open("_bmad-output/implementation-artifacts/investigations/test-doc.md", "rb") as f:
    content = f.read()

b64_content = base64.b64encode(content).decode("utf-8")

# 构造请求体
request_body = {
    "sources": [
        {
            "kind": "file",
            "base64_string": b64_content,
            "filename": "test-doc.md"
        }
    ],
    "convert_options": {
        "to_formats": ["md", "html", "text", "doctags"]
    },
    "include_converted_doc": True,
    "chunking_options": {
        "max_tokens": 512,
        "merge_peers": True
    }
}

print("=== Request ===")
print(f"Endpoint: POST http://localhost:5001/v1/chunk/hybrid/source")
print(f"File size: {len(content)} bytes")
print(f"Base64 size: {len(b64_content)} chars")
print(f"Base64 ends with: '{b64_content[-4:]}'")
print()

# 发送请求
req = urllib.request.Request(
    "http://localhost:5001/v1/chunk/hybrid/source",
    data=json.dumps(request_body).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST"
)

try:
    with urllib.request.urlopen(req, timeout=60) as resp:
        status = resp.status
        body = json.loads(resp.read().decode("utf-8"))

        print(f"=== Response ===")
        print(f"Status: {status}")
        print(f"Chunks: {len(body.get('chunks', []))}")
        print(f"Documents: {len(body.get('documents', []))}")

        if body.get("documents"):
            doc = body["documents"][0]
            print(f"\n=== Document ===")
            print(f"Status: {doc.get('status')}")
            c = doc.get("content", {})
            print(f"Content keys: {list(c.keys()) if c else 'None'}")
            for field in ["md_content", "html_content", "text_content", "doctags_content"]:
                val = c.get(field)
                if val:
                    print(f"  {field}: PRESENT ({len(val)} chars)")
                else:
                    print(f"  {field}: NULL")

        if body.get("chunks"):
            print(f"\n=== First Chunk ===")
            chunk = body["chunks"][0]
            text = chunk.get("text", "")
            print(f"Text ({len(text)} chars): {text[:300]}")

except urllib.error.HTTPError as e:
    print(f"HTTP Error: {e.code}")
    print(f"Response: {e.read().decode('utf-8')[:500]}")
except Exception as e:
    print(f"Exception: {e}")
