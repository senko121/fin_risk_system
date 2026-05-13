import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator

# Load model đã train
model = tf.keras.models.load_model("my_emotion_model.h5")

# Dataset test
test_dir = "dataset/test"

test_datagen = ImageDataGenerator(rescale=1./255)

test_data = test_datagen.flow_from_directory(
    test_dir,
    target_size=(48,48),
    batch_size=64,
    color_mode="grayscale",
    class_mode="categorical",
    shuffle=False
)

# Evaluate
loss, accuracy = model.evaluate(test_data)

print(f"Test Loss: {loss:.4f}")
print(f"Test Accuracy: {accuracy * 100:.2f}%")