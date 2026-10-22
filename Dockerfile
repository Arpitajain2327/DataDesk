FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN mkdir -p out && javac -cp "lib/*" -d out src/*.java
CMD ["java", "-cp", "out:lib/*", "App"]