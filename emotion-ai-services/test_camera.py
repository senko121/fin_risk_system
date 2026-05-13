import cv2
import numpy as np
from tensorflow.keras.models import load_model

# 1. Load cái model bro vừa train xong
model = load_model("my_emotion_model.h5")

loss, accuracy = model.evaluate(test_data)

print(f"Test Loss: {loss:.4f}")
print(f"Test Accuracy: {accuracy * 100:.2f}%")

print("🔥 MODEL TRAIN XONG!")

# 2. Danh sách nhãn khớp với các folder dataset
emotion_labels = ['Angry', 'Disgust', 'Fear', 'Happy', 'Neutral', 'Sad', 'Surprise']

# 3. Dùng bộ lọc mặt có sẵn của OpenCV
face_classifier = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

cap = cv2.VideoCapture(0)

while True:
    ret, frame = cap.read()
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    faces = face_classifier.detectMultiScale(gray, 1.3, 5)

    for (x, y, w, h) in faces:
        # Cắt vùng mặt, resize về 48x48, chuẩn hóa
        roi_gray = gray[y:y+h, x:x+w]
        roi_gray = cv2.resize(roi_gray, (48, 48), interpolation=cv2.INTER_AREA)
        
        if np.sum([roi_gray]) != 0:
            roi = roi_gray.astype('float') / 255.0
            roi = np.expand_dims(roi, axis=0) # (1, 48, 48, 1)

            # Dự đoán
            prediction = model.predict(roi)[0]
            label = emotion_labels[prediction.argmax()]
            prob = str(round(np.max(prediction) * 100, 2)) + "%"

            # Vẽ lên màn hình
            color = (0, 255, 0) if label == 'Happy' else (0, 0, 255)
            cv2.rectangle(frame, (x, y), (x+w, y+h), color, 2)
            cv2.putText(frame, f"{label} {prob}", (x, y-10), cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2)
        else:
            cv2.putText(frame, "No Face Found", (20, 60), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)

    cv2.imshow('FinRisk AI - Emotion Test', frame)
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()