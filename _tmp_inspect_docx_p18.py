# -*- coding: utf-8 -*-
from docx import Document
from pathlib import Path

p = Path(r"c:\Users\ASUS\Desktop\MyMmorpg项目全方位详细介绍文档.docx")
doc = Document(str(p))
for i, para in enumerate(doc.paragraphs):
    t = (para.text or "").strip()
    style = para.style.name if para.style else ""
    keys = [
        "P16", "P17", "P18", "3.2.1", "5.2.1", "生成日期", "修订说明",
        "Q17", "Q18", "11.18", "11.19", "5.2.15", "5.2.16", "3.2.13", "3.2.14",
        "P5～P16", "P5～P17", "open-world-top-tier",
    ]
    if any(k in t for k in keys):
        print(f"{i}|{style}|{t[:160]}")
