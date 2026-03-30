FROM nginx:1.25.4-alpine

# Copy the custom Nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Expose port 80 for proxy routing
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
