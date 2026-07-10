//This is a health check file to check the health of the server "AKA Ping"

class Ping {
  const Ping({required this.status, required this.message, required this.serverTime});

  final String status;

  final String message;

  final String serverTime;

  static Ping fromJson(Map<String, Object?> json) {
    return Ping(
      status: json['status'] as String,
      message: json['message'] as String,
      serverTime: json['serverTime'] as String,
    );
  }

  @override
  String toString() =>
      'Message{'
      'status: $status, '
      'message: $message, '
      'serverTime: $serverTime'
      '}';
}