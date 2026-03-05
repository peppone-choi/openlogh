FROM python:3-alpine


RUN pip3 install requests && \
    rm -r /root/.cache

COPY install_hidche.py /bin
RUN chmod +x /bin/install_hidche.py
CMD install_hidche.py