# RAG Spring Boot — Sample Data Test Guide

## Documents included
| File | Topics covered |
|------|---------------|
| `acme-employee-handbook.txt` | Refunds, leave, remote work, performance, IT security, expenses, conduct |
| `acme-product-catalog.txt`   | Products, prices, warranties, delivery, SaaS refund policy |

---

## STEP 1 — Upload both documents

```bash
# Upload handbook
curl -X POST http://localhost:8080/api/documents/ingest \
     -F "file=@acme-employee-handbook.txt"

# Upload catalog
curl -X POST http://localhost:8080/api/documents/ingest \
     -F "file=@acme-product-catalog.txt"
```

### Expected response for each upload:
```json
{
  "fileName": "acme-employee-handbook.txt",
  "chunksStored": 28,
  "status": "SUCCESS",
  "message": "Document ingested and embedded successfully"
}
```
> Why chunksStored varies: The splitter cuts text at ~500 chars.
> Longer documents produce more chunks. Both files together
> should produce 35-50 chunks stored in Qdrant.

---

## STEP 2 — Test Cases

Run each test with:
```bash
curl -X POST http://localhost:8080/api/rag/ask \
     -H "Content-Type: application/json" \
     -d '{"question": "QUESTION_HERE", "topK": 5}'
```

---

### TEST 1 — Direct fact retrieval (easy)
**Question:** `What is the refund policy for standard products?`

**Why this tests:** The answer is directly stated in Section 1.1.
The embedding of "refund policy" closely matches the chunk containing
"Standard Refund Policy". This is the baseline — your RAG MUST pass this.

**Expected answer should contain:**
- 30 days for full refund
- Unused and original packaging
- 5-7 business days to process
- Store credit after 30 days

---

### TEST 2 — Numeric/structured data retrieval
**Question:** `How many days of annual leave do full-time employees get?`

**Why this tests:** Verifies the model retrieves specific numbers
correctly from structured text. Tests that chunking didn't split
the number from its context (e.g. "20 days" split from "annual leave").

**Expected answer should contain:**
- 20 days paid annual leave
- Pro-rata for part-time employees
- 2 weeks advance notice required

---

### TEST 3 — Multi-condition question
**Question:** `What are the requirements to work from home?`

**Why this tests:** The answer spans multiple sentences in Section 3.1
and 3.2. Tests whether top-k retrieval fetches enough context when
the answer is spread across nearby chunks.

**Expected answer should contain:**
- 6-month probation completed
- Manager approval
- Max 3 days per week from home
- Min 2 days in office

---

### TEST 4 — Cross-document retrieval
**Question:** `What is the refund policy for the CloudDesk Pro software?`

**Why this tests:** This answer is in the PRODUCT CATALOG, not the
handbook. Tests that Qdrant correctly retrieves from multiple ingested
documents and the LLM synthesises from the right source.

**Expected answer should contain:**
- Monthly plans cancel anytime
- Annual plans refundable within 30 days pro-rated
- sourcesUsed: ["acme-product-catalog.txt"]

---

### TEST 5 — Bonus calculation (reasoning over retrieved data)
**Question:** `If an employee is rated 4 in their performance review, what bonus and salary increase do they get?`

**Why this tests:** The answer requires combining two facts from
Section 4.3 (bonus %) and Section 4.4 (salary increase %). Tests
whether the LLM can reason over multiple retrieved chunks from the
same document without hallucinating numbers.

**Expected answer should contain:**
- 10% annual bonus
- Up to 5% salary increase
- Bonus paid in January

---

### TEST 6 — Negative test (question NOT in documents)
**Question:** `What is the CEO's name?`

**Why this tests:** This information does NOT exist in either document.
The RAG system must say it doesn't know — it must NOT hallucinate an
answer from the LLM's general training data. This is critical for
production reliability.

**Expected answer:**
> "I could not find relevant information in the uploaded documents."

If the LLM gives a made-up name — your system prompt is not strong
enough. Consider making the instruction more explicit.

---

### TEST 7 — Ambiguous question (tests retrieval precision)
**Question:** `What is the password policy?`

**Why this tests:** "Password policy" appears in Section 5.1. Tests
whether the embedding model correctly disambiguates "password policy"
from other policy sections (leave policy, refund policy, etc.)
in the vector similarity search.

**Expected answer should contain:**
- Minimum 12 characters
- Uppercase, lowercase, numbers, special characters
- Change every 90 days
- No reuse of last 10 passwords

---

### TEST 8 — Product price lookup
**Question:** `How much does the ProLaptop X1 cost and what warranty does it come with?`

**Why this tests:** Tests retrieval of two related facts (price + warranty)
that are close together in the product catalog chunk. Also verifies the
model reports the correct source file.

**Expected answer should contain:**
- $1,299
- 2 years parts and labor
- Extended warranty $199/year
- sourcesUsed: ["acme-product-catalog.txt"]

---

### TEST 9 — topK override (retrieval depth test)
**Question:** `Summarise all the leave types available to employees`

```bash
curl -X POST http://localhost:8080/api/rag/ask \
     -H "Content-Type: application/json" \
     -d '{"question": "Summarise all the leave types available to employees", "topK": 10}'
```

**Why this tests:** Leave types span 4 sub-sections (2.1–2.4). With
topK=5 the LLM might miss one type. With topK=10 all leave chunks
should be retrieved. Compare the answers at topK=3 vs topK=10 to
see the difference retrieval depth makes.

**Expected answer at topK=10 should contain all 4 types:**
- Annual leave (20 days)
- Sick leave (10 days)
- Maternity leave (16 weeks) / Paternity leave (4 weeks)
- Emergency leave (3 days)

---

### TEST 10 — GET endpoint quick test (browser-friendly)
Open directly in your browser:

```
http://localhost:8080/api/rag/ask?q=What+are+the+meal+allowance+limits+for+business+meals
```

**Why this tests:** Validates the GET endpoint works and URL-encoded
questions are correctly parsed by Spring MVC.

**Expected answer should contain:**
- $75 per person for client meals
- $30 per person for team meals
- Alcohol up to 20% of meal cost for client entertainment only

---

## STEP 3 — What good responses look like

A healthy response JSON looks like this:
```json
{
  "question": "What is the refund policy for standard products?",
  "answer": "Customers may request a full refund within 30 days of purchase for any product that is unused and in its original packaging. Refunds are processed within 5-7 business days to the original payment method. After 30 days, only store credit is available.",
  "sourcesUsed": ["acme-employee-handbook.txt"],
  "processingTimeMs": 2341
}
```

## STEP 4 — Diagnosing problems

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Answer is empty / very short | topK too low, chunks too small | Increase topK or chunk-size |
| Wrong document cited in sourcesUsed | Embedding collision between docs | Use more specific questions |
| LLM invents facts not in document | System prompt too weak | Add "ONLY use the context below" more forcefully |
| processingTimeMs > 30000 | Ollama running slowly on CPU | Normal for llama3 on CPU — try llama3:8b |
| 500 error on ingest | Qdrant not running | Check `docker compose ps` |
| chunksStored = 0 | File couldn't be parsed | Check file isn't password-protected |
