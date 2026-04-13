FROM nginx:1.25.4-alpine

# Remove default nginx welcome page config
RUN rm /etc/nginx/conf.d/default.conf

# Copy the custom Nginx template configuration
COPY nginx.conf.template /etc/nginx/templates/nginx.conf.template

# Expose port 80 for proxy routing
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
