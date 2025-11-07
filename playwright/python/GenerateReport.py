import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

# Paths
repo_root = Path(__file__).parent.resolve()
json_path = repo_root / "report.json"
report_dir = repo_root / "docs"
report_dir.mkdir(exist_ok=True)

step1 = report_dir / "step1.png"
step2 = report_dir / "step2.png"
report_html = report_dir / "report.html"

# Read username
def read_username():
    try:
        with open(json_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            return data.get("username", "")
    except Exception as e:
        print(f"❌ Failed to read {json_path}: {e}")
        return ""

def generate_html_report(step1_img, step2_img, filled_ok, cleared_ok, username):
    html = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Playwright Test Report</title>
  <style>
    body {{ font-family: Arial, sans-serif; padding: 18px; }}
    table {{ border-collapse: collapse; width: 100%; max-width: 900px; }}
    th, td {{ border: 1px solid #ccc; padding: 8px; text-align: left; }}
    th {{ background: #f2f2f2; }}
    .pass {{ color: green; font-weight: bold; }}
    .fail {{ color: red; font-weight: bold; }}
    img.sshot {{ max-width: 320px; border: 1px solid #999; }}
  </style>
</head>
<body>
<h1>Automated Playwright Test Report</h1>
<p><strong>Username value used:</strong> {username or "(Not Found in report.json)"}</p>
<table>
  <thead>
    <tr><th>No.</th><th>Step</th><th>Result</th><th>Screenshot</th></tr>
  </thead>
  <tbody>
    <tr>
      <td>1.</td>
      <td>Fill value in username field.</td>
      <td class="{ 'pass' if filled_ok else 'fail' }">{ 'PASS' if filled_ok else 'FAIL' }</td>
      <td><img class="sshot" src="{step1_img}" alt="step1"></td>
    </tr>
    <tr>
      <td>2.</td>
      <td>Empty value in username field.</td>
      <td class="{ 'pass' if cleared_ok else 'fail' }">{ 'PASS' if cleared_ok else 'FAIL' }</td>
      <td><img class="sshot" src="{step2_img}" alt="step2"></td>
    </tr>
  </tbody>
</table>
<p>Generated automatically by <b>Playwright</b>.</p>
</body>
</html>"""
    report_html.write_text(html, encoding="utf-8")
    print(f"📄 Report generated: {report_html.absolute()}")

def run_test():
    username = read_username()

    print("🌐 Launching browser...")
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1920, "height": 1080})
        page = context.new_page()

        page.goto("https://login.salesforce.com/")
        print("✅ Opened Salesforce login page")

        filled_ok = cleared_ok = False
        try:
            username_field = page.locator("#username")
            username_field.fill(username)
            filled_value = username_field.input_value()
            filled_ok = bool(username and filled_value == username)
            page.screenshot(path=step1)
            print(f"📸 step1.png captured — username filled check: {'PASS' if filled_ok else 'FAIL'}")

            username_field.fill("")  # clear
            cleared_value = username_field.input_value()
            cleared_ok = cleared_value == ""
            page.screenshot(path=step2)
            print(f"📸 step2.png captured — username cleared check: {'PASS' if cleared_ok else 'FAIL'}")

        except Exception as e:
            print(f"❌ Error during test: {e}")
        finally:
            browser.close()

        generate_html_report(step1.name, step2.name, filled_ok, cleared_ok, username)

if __name__ == "__main__":
    run_test()
