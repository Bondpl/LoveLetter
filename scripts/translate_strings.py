#!/usr/bin/env python3
"""
LoveLetter Android String Translator
Automatically translates app/src/main/res/values/strings.xml into 48+ languages
and generates Android resource directories (values-<locale>/strings.xml).
"""

import os
import re
import sys
import time
import urllib.request
import urllib.parse
import json
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
RES_DIR = BASE_DIR / "app" / "src" / "main" / "res"
SOURCE_STRINGS = RES_DIR / "values" / "strings.xml"

# Map of Google Play / Android target locales: (google_translate_code, android_folder_name, language_name)
LANGUAGES = [
    ("pl", "values-pl", "Polish"),
    ("de", "values-de", "German"),
    ("es", "values-es", "Spanish"),
    ("fr", "values-fr", "French"),
    ("it", "values-it", "Italian"),
    ("pt", "values-pt", "Portuguese"),
    ("pt-BR", "values-pt-rBR", "Portuguese (Brazil)"),
    ("nl", "values-nl", "Dutch"),
    ("uk", "values-uk", "Ukrainian"),
    ("ru", "values-ru", "Russian"),
    ("cs", "values-cs", "Czech"),
    ("sk", "values-sk", "Slovak"),
    ("hu", "values-hu", "Hungarian"),
    ("ro", "values-ro", "Romanian"),
    ("bg", "values-bg", "Bulgarian"),
    ("hr", "values-hr", "Croatian"),
    ("sr", "values-sr", "Serbian"),
    ("sl", "values-sl", "Slovenian"),
    ("sv", "values-sv", "Swedish"),
    ("da", "values-da", "Danish"),
    ("fi", "values-fi", "Finnish"),
    ("no", "values-no", "Norwegian"),
    ("el", "values-el", "Greek"),
    ("tr", "values-tr", "Turkish"),
    ("ja", "values-ja", "Japanese"),
    ("ko", "values-ko", "Korean"),
    ("zh-CN", "values-zh-rCN", "Chinese (Simplified)"),
    ("zh-TW", "values-zh-rTW", "Chinese (Traditional)"),
    ("vi", "values-vi", "Vietnamese"),
    ("th", "values-th", "Thai"),
    ("id", "values-in", "Indonesian"),
    ("ms", "values-ms", "Malay"),
    ("hi", "values-hi", "Hindi"),
    ("bn", "values-bn", "Bengali"),
    ("ar", "values-ar", "Arabic"),
    ("he", "values-iw", "Hebrew"),
    ("fa", "values-fa", "Persian"),
    ("lt", "values-lt", "Lithuanian"),
    ("lv", "values-lv", "Latvian"),
    ("et", "values-et", "Estonian"),
    ("ca", "values-ca", "Catalan"),
    ("fil", "values-tl", "Filipino / Tagalog"),
    ("ur", "values-ur", "Urdu"),
    ("sw", "values-sw", "Swahili"),
    ("af", "values-af", "Afrikaans"),
    ("az", "values-az", "Azerbaijani"),
    ("ka", "values-ka", "Georgian"),
    ("kk", "values-kk", "Kazakh"),
]

# Keys that should NEVER be translated (brand names, technical codes, etc.)
DO_NOT_TRANSLATE_KEYS = {
    "app_name",
    "color_picker_rgb_format",
    "notif_partner_left_title",
}

# Specific manual overrides for natural phrasing in specific languages
OVERRIDE_TRANSLATIONS = {
    ("pl", "btn_cancel"): "Anuluj",
    ("pl", "btn_close"): "Zamknij",
    ("pl", "auth_divider_or"): "LUB",
    ("pl", "auth_btn_connect"): "Połącz",
    ("pl", "auth_btn_create_pair"): "Utwórz kod pary",
    ("pl", "waiting_btn_copy_code"): "Kopiuj kod",
    ("pl", "waiting_btn_cancel_pairing"): "Anuluj łączenie",
    ("pl", "topbar_btn_disconnect"): "Rozłącz",
    ("pl", "topbar_partners_art"): "Rysunek partnera",
    ("pl", "dialog_partner_art_title"): "Rysunek partnera",
    ("pl", "dialog_btn_back_to_canvas"): "Wróć do rysowania",
    ("pl", "dialog_photo_preview_btn_send"): "Wyślij zdjęcie",
    ("pl", "canvas_send_drawing"): "Wyślij rysunek",
    ("pl", "canvas_sending"): "Wysyłanie&#8230;",
    ("pl", "canvas_clear"): "Wyczyść",
    ("pl", "canvas_undo"): "Cofnij",
    ("pl", "canvas_redo"): "Ponów",
    ("pl", "canvas_fill_canvas"): "Wypełnij tło",
    ("pl", "canvas_pick_photo"): "Wybierz zdjęcie",
    ("pl", "canvas_brush_marker"): "Marker",
    ("pl", "toast_view_once_photo_sent"): "Wysłano zdjęcie jednorazowe!",
    ("pl", "notif_view_once_photo_title"): "Zdjęcie jednorazowe",
    ("pl", "notif_view_once_photo_body"): "Twój partner wysłał Ci zdjęcie do jednorazowego wyświetlenia!",
}

def escape_android_xml(text: str) -> str:
    """Escapes special characters according to Android strings.xml specification."""
    text = text.replace("&amp;", "&")
    text = re.sub(r"&(?!#\d+;|#x[0-9a-fA-F]+;|[a-zA-Z]+;)", "&amp;", text)
    text = re.sub(r"(?<!\\)'", r"\'", text)
    text = re.sub(r'(?<!\\)"', r'\"', text)
    text = text.replace("...", "&#8230;")
    return text

def translate_text(text: str, target_lang: str, retries: int = 3) -> str:
    """Translates text using Google Translate free web API, protecting placeholders and brand names."""
    if not text.strip():
        return text

    # Protect Android placeholders like %1$d, %1$s, %d, %s, etc.
    placeholders = []
    def replace_ph(match):
        idx = len(placeholders)
        placeholders.append(match.group(0))
        return f"ZZPH{idx}ZZ"

    protected = re.sub(r"%(\d+\$)?[a-zA-Z]", replace_ph, text)
    # Protect brand name LoveLetter
    protected = protected.replace("LoveLetter", "ZZBRANDZZ")
    # Protect circled number
    protected = protected.replace("①", "ZZCIRC1ZZ")

    url = (
        "https://translate.googleapis.com/translate_a/single?"
        + urllib.parse.urlencode({
            "client": "gtx",
            "sl": "en",
            "tl": target_lang,
            "dt": "t",
            "q": protected,
        })
    )

    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        }
    )

    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                data = json.loads(response.read().decode("utf-8"))
                translated = "".join(part[0] for part in data[0] if part[0])

                # Restore brand name
                translated = re.sub(r"ZZ\s*BRAND\s*ZZ", "LoveLetter", translated, flags=re.IGNORECASE)

                # Restore circled number
                translated = re.sub(r"ZZ\s*CIRC\s*1\s*ZZ", "①", translated, flags=re.IGNORECASE)

                # Restore placeholders (handle possible spacing added by translator)
                for idx, ph in enumerate(placeholders):
                    pattern = re.compile(rf"ZZ\s*PH\s*{idx}\s*ZZ", re.IGNORECASE)
                    translated = pattern.sub(ph, translated)

                return translated
        except Exception as e:
            if attempt == retries - 1:
                print(f"    [Error] Failed to translate: '{text[:25]}...' -> {e}")
                return text
            time.sleep(1.0)
    return text

def parse_english_strings(file_path: Path):
    """Parses strings.xml and returns a list of (name, value)."""
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    pattern = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.DOTALL)
    entries = []
    for match in pattern.finditer(content):
        name = match.group(1)
        value = match.group(2)
        clean_value = (
            value.replace(r"\'", "'")
            .replace(r'\"', '"')
            .replace("&amp;", "&")
            .replace("&#8230;", "...")
            .replace(r"\u2460", "①")
            .replace(r"\u2014", "—")
        )
        entries.append((name, clean_value))
    return entries

def translate_for_locale(target_code: str, folder_name: str, lang_name: str, entries):
    out_dir = RES_DIR / folder_name
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / "strings.xml"

    print(f"\n[*] Translating to {lang_name} ({folder_name})...")
    lines = ['<resources>']

    for name, en_value in entries:
        if name in DO_NOT_TRANSLATE_KEYS:
            trans_value = en_value
        elif (target_code, name) in OVERRIDE_TRANSLATIONS:
            trans_value = OVERRIDE_TRANSLATIONS[(target_code, name)]
        else:
            trans_value = translate_text(en_value, target_code)
            time.sleep(0.04)

        escaped = escape_android_xml(trans_value)
        lines.append(f'    <string name="{name}">{escaped}</string>')

    lines.append('</resources>\n')

    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print(f"    -> Saved: {out_file.relative_to(BASE_DIR)}")

def main():
    if not SOURCE_STRINGS.exists():
        print(f"Error: {SOURCE_STRINGS} not found!")
        sys.exit(1)

    entries = parse_english_strings(SOURCE_STRINGS)
    print(f"Found {len(entries)} strings in {SOURCE_STRINGS}")

    target_langs = LANGUAGES
    if len(sys.argv) > 1:
        requested = sys.argv[1].lower().strip("-")
        if requested not in ("all", "--all"):
            filtered = [
                lang for lang in LANGUAGES
                if lang[0].lower() == requested or lang[1].lower().endswith(requested)
            ]
            if not filtered:
                print(f"Unknown language '{requested}'. Available: {[l[0] for l in LANGUAGES]}")
                sys.exit(1)
            target_langs = filtered

    print(f"Starting translation for {len(target_langs)} language(s)...")
    for code, folder, name in target_langs:
        translate_for_locale(code, folder, name, entries)

    print("\n[SUCCESS] Translations completed successfully!")

if __name__ == "__main__":
    main()
