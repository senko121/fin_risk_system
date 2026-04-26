export const convertWordsToNumbers = (spokenText) => {
    // Từ điển ép kiểu Tiếng Việt -> Số
    const dictionary = {
        "không": "0", "ok": "0", "o": "0", // Thêm mấy case đọc nhiễu
        "một": "1", "mốt": "1",
        "hai": "2",
        "ba": "3",
        "bốn": "4", "tư": "4",
        "năm": "5", "lăm": "5", "nhăm": "5",
        "sáu": "6", "sáo": "6", // Vosk thỉnh thoảng ngọng
        "bảy": "7", "bẩy": "7",
        "tám": "8",
        "chín": "9"
    };

    if (!spokenText) return "";

    // 1. Chuyển thành chữ thường và cắt bỏ khoảng trắng thừa
    const words = spokenText.toLowerCase().trim().split(/\s+/);
    
    // 2. Map từng chữ sang số, bỏ qua những từ rác không có trong từ điển
    let resultNumeric = "";
    words.forEach(word => {
        if (dictionary[word] !== undefined) {
            resultNumeric += dictionary[word];
        }
    });

    return resultNumeric;
};

// Test thử:
// console.log(convertWordsToNumbers("không một hai ba tư lăm")); // Output: "012345"
// console.log(convertWordsToNumbers("nhận diện số một ba bảy chín ok chưa")); // Output: "13790"