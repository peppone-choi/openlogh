FROM mariadb:11
#ubuntu

COPY entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/entrypoint.sh
ENTRYPOINT ["entrypoint.sh"]


EXPOSE 3306
CMD ["mariadbd"]