ARG PYTHON_BASE_IMAGE=python:3.11-slim
FROM ${PYTHON_BASE_IMAGE}

WORKDIR /app

ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
ARG PIP_EXTRA_INDEX_URL=
ARG PIP_DEFAULT_TIMEOUT=120

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_INDEX_URL=${PIP_INDEX_URL} \
    PIP_EXTRA_INDEX_URL=${PIP_EXTRA_INDEX_URL} \
    PIP_DEFAULT_TIMEOUT=${PIP_DEFAULT_TIMEOUT}

COPY requirements.txt .
RUN python -m pip install --no-cache-dir --upgrade pip \
    && python -m pip install --no-cache-dir --retries 10 -r requirements.txt \
    && python -m pip uninstall -y opencv-python opencv-contrib-python opencv-contrib-python-headless || true \
    && python -m pip install --no-cache-dir --retries 10 opencv-contrib-python-headless==4.10.0.84

COPY app.py .

EXPOSE 9000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "9000"]
