import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Conv2D, MaxPooling2D, Flatten, Dense, Dropout, BatchNormalization
from tensorflow.keras.optimizers import Adam
import os

# 0. Cấu hình để dùng GPU GTX 1650 hiệu quả
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
gpus = tf.config.list_physical_devices('GPU')
if gpus:
    try:
        for gpu in gpus:
            tf.config.experimental.set_memory_growth(gpu, True)
        print("✅ Đã kết nối với GPU: GTX 1650")
    except RuntimeError as e:
        print(e)

# 1. Đường dẫn (Đảm bảo folder dataset nằm cùng cấp file này)
train_dir = "dataset/train"
test_dir = "dataset/test"

# 2. Augmentation (Tạo thêm dữ liệu giả lập cho phong phú)
train_datagen = ImageDataGenerator(
    rescale=1./255,
    rotation_range=15,
    width_shift_range=0.1,
    height_shift_range=0.1,
    shear_range=0.1,
    zoom_range=0.1,
    horizontal_flip=True,
    fill_mode='nearest'
)
test_datagen = ImageDataGenerator(rescale=1./255)

train_data = train_datagen.flow_from_directory(
    train_dir, target_size=(48,48), batch_size=64, # Tăng batch size lên 64 vì có GPU
    color_mode="grayscale", class_mode="categorical"
)

test_data = test_datagen.flow_from_directory(
    test_dir, target_size=(48,48), batch_size=64,
    color_mode="grayscale", class_mode="categorical"
)

# 3. Xây dựng Kiến trúc CNN "Tự Chế" mạnh mẽ hơn
model = Sequential()

# Layer 1
model.add(Conv2D(32, (3,3), padding='same', activation='relu', input_shape=(48,48,1)))
model.add(BatchNormalization())
model.add(MaxPooling2D(2,2))
model.add(Dropout(0.25))

# Layer 2
model.add(Conv2D(64, (5,5), padding='same', activation='relu'))
model.add(BatchNormalization())
model.add(MaxPooling2D(2,2))
model.add(Dropout(0.25))

# Layer 3
model.add(Conv2D(128, (3,3), padding='same', activation='relu'))
model.add(BatchNormalization())
model.add(MaxPooling2D(2,2))
model.add(Dropout(0.25))

# Layer 4
model.add(Conv2D(256, (3,3), padding='same', activation='relu'))
model.add(BatchNormalization())
model.add(MaxPooling2D(2,2))
model.add(Dropout(0.25))

model.add(Flatten())

# Fully Connected Layers
model.add(Dense(256, activation='relu'))
model.add(BatchNormalization())
model.add(Dropout(0.5))

model.add(Dense(7, activation='softmax'))

# 4. Compile
model.compile(
    optimizer=Adam(learning_rate=0.001),
    loss='categorical_crossentropy',
    metrics=['accuracy']
)

# 5. Train (Để 30-50 epochs cho nó khôn hẳn)
history = model.fit(
    train_data,
    validation_data=test_data,
    epochs=50 # GPU chạy 50 cái này vèo phát xong
)

# 6. Lưu thành quả
model.save("my_emotion_model.h5")
print("🔥 CHÚC MỪNG BRO! MODEL TỰ THÂN ĐÃ XONG!")