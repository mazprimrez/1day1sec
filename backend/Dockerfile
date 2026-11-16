FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app app
COPY prompts prompts

# Cloud Run runs the container as it is told to; do not run as root.
USER nobody

# Cloud Run sets PORT. One worker: Cloud Run adds instances, not processes.
CMD ["sh", "-c", "exec uvicorn app.main:create_app --factory --host 0.0.0.0 --port ${PORT:-8080}"]
