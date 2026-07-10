import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'ping.dart';
import 'package:http/http.dart';

void main() {
  runApp(const MainApp());
}

class MainApp extends StatelessWidget {
  const MainApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: ApiView()
    );
  }
}

class ApiModel {
  Future<Ping> getApiHealth() async {
    final uri = Uri.http(
      'localhost:8000',
      '/api/v1/ping'
    );

    final response = await get(uri);

    if (response.statusCode != 200) {
      throw const HttpException('Failed to update resource');
    }

    return Ping.fromJson(jsonDecode(response.body) as Map<String, Object?>);
  }
}


class ApiView extends StatefulWidget {
  const ApiView({super.key});

  @override
  State<ApiView> createState() => _ApiViewState();
}

class ApiViewModel extends ChangeNotifier{
  final ApiModel model;

  Ping? ping;
  Exception? error;
  bool isLoading = false;

  ApiViewModel(this.model) {
    pingApi();
  }

  Future<void> pingApi() async {
    isLoading = true;
    notifyListeners();
    try{
      ping = await model.getApiHealth();
      error = null;
      print('Ping health checked ${ping!.message}');
    } on HttpException catch (e) {
      error = e;
      print('Error checking health ${e.message}');
      ping = null;
    }
    isLoading = false;
    notifyListeners();
  }
}

class _ApiViewState extends State<ApiView> {
  final ApiViewModel viewModel = ApiViewModel(ApiModel());

  @override
  void initState() {
    super.initState();
    viewModel.pingApi();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('API healthcheck')),
      body: ListenableBuilder(
          listenable: viewModel,
          builder: (context, child) {
            return switch ((
            viewModel.isLoading,
            viewModel.ping,
            viewModel.error,
            )) {
            (true, _, _) => const CircularProgressIndicator(),
            (_, _, final Exception e) => Text('Error: $e'),
            (_,final ping?, _) =>  ApiPage(
              ping: ping,
              pingApi: viewModel.pingApi,
            ),
            _ => const Text('Something went wrong'),
            };
          }
      ),
    );
  }
}

class ApiPage extends StatelessWidget {
  const ApiPage({super.key, required this.ping, required this.pingApi});

  final Ping ping;

  final VoidCallback pingApi;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        children: [
          ApiWidget(ping: ping),
          ElevatedButton(onPressed: pingApi, child: const Text('Ping API'),
          ),
        ]
      )
    );
  }
}

class ApiWidget extends StatelessWidget {
  const ApiWidget({super.key, required this.ping});

  final Ping ping;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Column(
        spacing: 10,
        children: [Text(ping.toString())],
    )
    );
  }
}
