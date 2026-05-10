import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(const WarehouseMobileApp());
}

class WarehouseMobileApp extends StatelessWidget {
  const WarehouseMobileApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Склад РЭУ',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF1F6FD1),
          brightness: Brightness.light,
        ),
        scaffoldBackgroundColor: const Color(0xFFEDF4FB),
        useMaterial3: true,
      ),
      home: const AppRoot(),
    );
  }
}

class AppRoot extends StatefulWidget {
  const AppRoot({super.key});

  @override
  State<AppRoot> createState() => _AppRootState();
}

class _AppRootState extends State<AppRoot> {
  ApiClient? _api;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _restore();
  }

  Future<void> _restore() async {
    final prefs = await SharedPreferences.getInstance();
    final baseUrl = prefs.getString('baseUrl');
    final username = prefs.getString('username');
    final password = prefs.getString('password');
    if (baseUrl != null && username != null && password != null) {
      _api = ApiClient(baseUrl: baseUrl, username: username, password: password);
    }
    setState(() => _loading = false);
  }

  Future<void> _login(ApiClient api) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('baseUrl', api.baseUrl);
    await prefs.setString('username', api.username);
    await prefs.setString('password', api.password);
    setState(() => _api = api);
  }

  Future<void> _logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    setState(() => _api = null);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final api = _api;
    if (api == null) {
      return LoginScreen(onLogin: _login);
    }
    return WarehouseHome(api: api, onLogout: _logout);
  }
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.onLogin});

  final ValueChanged<ApiClient> onLogin;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _baseUrl = TextEditingController(text: 'http://10.124.145.191:9092');
  final _username = TextEditingController(text: 'warehouse');
  final _password = TextEditingController(text: 'admin123');
  bool _loading = false;
  String? _error;

  Future<void> _submit() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final api = ApiClient(
      baseUrl: _baseUrl.text.trim(),
      username: _username.text.trim(),
      password: _password.text,
    );
    try {
      await api.get('/api/warehouse/me');
      widget.onLogin(api);
    } catch (error) {
      setState(() => _error = error.toString());
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            const SizedBox(height: 28),
            const Text('Склад РЭУ', style: TextStyle(fontSize: 38, fontWeight: FontWeight.w900)),
            const SizedBox(height: 8),
            const Text('Мобильное рабочее место складовщика'),
            const SizedBox(height: 28),
            AppTextField(controller: _baseUrl, label: 'Адрес сервера'),
            AppTextField(controller: _username, label: 'Логин'),
            AppTextField(controller: _password, label: 'Пароль', obscure: true),
            if (_error != null) ErrorBox(_error!),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _loading ? null : _submit,
              child: Text(_loading ? 'Подключение...' : 'Войти'),
            ),
          ],
        ),
      ),
    );
  }
}

class WarehouseHome extends StatefulWidget {
  const WarehouseHome({super.key, required this.api, required this.onLogout});

  final ApiClient api;
  final VoidCallback onLogout;

  @override
  State<WarehouseHome> createState() => _WarehouseHomeState();
}

class _WarehouseHomeState extends State<WarehouseHome> {
  int _tab = 0;
  bool _loading = true;
  String? _error;
  List<dynamic> _buildings = [];
  List<dynamic> _locations = [];
  List<dynamic> _receiptRequests = [];
  List<dynamic> _disposalDue = [];
  Map<String, dynamic>? _inventoryState;
  Map<String, dynamic>? _disposalState;
  int? _buildingId;

  @override
  void initState() {
    super.initState();
    _loadInitial();
  }

  Future<void> _loadInitial() async {
    await _guard(() async {
      _buildings = await widget.api.getList('/api/warehouse/buildings');
      if (_buildings.isNotEmpty) {
        _buildingId = _buildings.first['id'] as int;
        await _loadBuildingData();
      }
    });
  }

  Future<void> _loadBuildingData() async {
    final buildingId = _buildingId;
    if (buildingId == null) {
      _locations = [];
      _receiptRequests = [];
      _disposalDue = [];
      _inventoryState = null;
      _disposalState = null;
      return;
    }
    _locations = await widget.api.getList('/api/warehouse/locations?buildingId=$buildingId');
    _receiptRequests = await widget.api.getList('/api/warehouse/receipt-requests?buildingId=$buildingId');
    _disposalDue = await widget.api.getList('/api/warehouse/disposal-due?buildingId=$buildingId');
    _inventoryState = await widget.api.get('/api/warehouse/inventory/state?buildingId=$buildingId');
    _disposalState = await widget.api.get('/api/warehouse/disposal/state?buildingId=$buildingId');
  }

  Future<void> _guard(Future<void> Function() action) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await action();
    } catch (error) {
      _error = cleanupError(error);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _action(Future<void> Function() action) async {
    await _guard(() async {
      await action();
      await _loadBuildingData();
    });
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      ReceiptPage(
        api: widget.api,
        buildingId: _buildingId,
        requests: _receiptRequests,
        onAction: _action,
      ),
      PlacementPage(
        api: widget.api,
        buildingId: _buildingId,
        locations: _locations,
        onAction: _action,
      ),
      InventoryPage(
        api: widget.api,
        buildingId: _buildingId,
        locations: _locations,
        state: _inventoryState,
        onAction: _action,
      ),
      DisposalPage(
        api: widget.api,
        buildingId: _buildingId,
        dueItems: _disposalDue,
        state: _disposalState,
        onAction: _action,
      ),
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Склад'),
        actions: [IconButton(onPressed: widget.onLogout, icon: const Icon(Icons.logout))],
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () => _guard(_loadBuildingData),
          child: ListView(
            padding: const EdgeInsets.all(14),
            children: [
              BuildingSelector(
                buildings: _buildings,
                value: _buildingId,
                onChanged: (value) => _guard(() async {
                  _buildingId = value;
                  await _loadBuildingData();
                }),
              ),
              if (_error != null) ErrorBox(_error!),
              if (_loading) const LinearProgressIndicator(),
              const SizedBox(height: 12),
              pages[_tab],
            ],
          ),
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (value) => setState(() => _tab = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.inventory_2), label: 'Приемка'),
          NavigationDestination(icon: Icon(Icons.meeting_room), label: 'Размещение'),
          NavigationDestination(icon: Icon(Icons.fact_check), label: 'Инвент.'),
          NavigationDestination(icon: Icon(Icons.delete_sweep), label: 'Утил.'),
        ],
      ),
    );
  }
}

class ReceiptPage extends StatefulWidget {
  const ReceiptPage({
    super.key,
    required this.api,
    required this.buildingId,
    required this.requests,
    required this.onAction,
  });

  final ApiClient api;
  final int? buildingId;
  final List<dynamic> requests;
  final Future<void> Function(Future<void> Function()) onAction;

  @override
  State<ReceiptPage> createState() => _ReceiptPageState();
}

class _ReceiptPageState extends State<ReceiptPage> {
  int? _requestId;
  final _code = TextEditingController();

  Map<String, dynamic>? get _selected {
    return widget.requests.cast<Map<String, dynamic>?>().firstWhere(
          (request) => request?['id'] == _requestId,
          orElse: () => null,
        );
  }

  Future<void> _start() async {
    final requestId = requireSelected(_requestId, 'Выберите заявку');
    await widget.onAction(() => widget.api.post('/api/warehouse/receipt/start', {
          'buildingId': widget.buildingId,
          'requestId': requestId,
        }));
  }

  Future<void> _scan([String? scannedCode]) async {
    final requestId = requireSelected(_requestId, 'Выберите заявку');
    final code = scannedCode ?? _code.text;
    await widget.onAction(() => widget.api.post('/api/warehouse/receipt/scan', {
          'buildingId': widget.buildingId,
          'requestId': requestId,
          'code': code,
        }));
    _code.clear();
  }

  Future<void> _finish() async {
    final requestId = requireSelected(_requestId, 'Выберите заявку');
    await widget.onAction(() => widget.api.post('/api/warehouse/receipt/finish', {
          'buildingId': widget.buildingId,
          'requestId': requestId,
        }));
  }

  @override
  Widget build(BuildContext context) {
    final selected = _selected;
    final status = selected?['status'] as String?;
    final items = (selected?['items'] as List<dynamic>?) ?? [];
    final canStart = status == 'SENT_TO_WAREHOUSE';
    final inProgress = status == 'IN_PROGRESS';
    final finished = status == 'ACCEPTED' || status == 'PARTIALLY_ACCEPTED' || status == 'NOT_ACCEPTED';

    return SectionCard(
      title: 'Приемка',
      children: [
        DropdownButtonFormField<int>(
          value: _requestId,
          decoration: const InputDecoration(labelText: 'Заявка экономиста'),
          items: widget.requests
              .map((request) => DropdownMenuItem<int>(
                    value: request['id'] as int,
                    child: Text('#${request['id']} · ${request['title']}'),
                  ))
              .toList(),
          onChanged: (value) => setState(() => _requestId = value),
        ),
        if (selected == null)
          EmptyText(widget.buildingId == null ? 'Выберите корпус.' : 'Выберите заявку для приемки.')
        else ...[
          StatusLine(
            icon: Icons.assignment_turned_in,
            title: selected['supplier'] as String? ?? '-',
            subtitle: '${selected['accepted']} из ${selected['total']} принято · $status',
          ),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: items
                .map((item) => Chip(
                      label: Text('${item['code']}${item['accepted'] == true ? ' ✓' : ''}'),
                      backgroundColor: item['accepted'] == true ? const Color(0xFFDFF6E4) : null,
                    ))
                .toList(),
          ),
          if (canStart) FilledButton.icon(onPressed: _start, icon: const Icon(Icons.play_arrow), label: const Text('Начать приемку')),
          if (inProgress) ...[
            CodeInput(
              controller: _code,
              onScan: (code) => _code.text = code,
              onScannedCode: _scan,
            ),
            FilledButton.icon(onPressed: () => _scan(), icon: const Icon(Icons.qr_code), label: const Text('Принять')),
            OutlinedButton.icon(onPressed: _finish, icon: const Icon(Icons.stop), label: const Text('Завершить приемку')),
          ],
          if (finished) const EmptyText('Приемка завершена. Акт доступен в веб-версии.'),
        ],
        const Divider(),
        ...widget.requests.take(6).map((request) => InfoTile(
              title: '#${request['id']} · ${request['title']}',
              subtitle: '${request['accepted']} из ${request['total']} · ${request['status']}',
            )),
      ],
    );
  }
}

class PlacementPage extends StatefulWidget {
  const PlacementPage({
    super.key,
    required this.api,
    required this.buildingId,
    required this.locations,
    required this.onAction,
  });

  final ApiClient api;
  final int? buildingId;
  final List<dynamic> locations;
  final Future<void> Function(Future<void> Function()) onAction;

  @override
  State<PlacementPage> createState() => _PlacementPageState();
}

class _PlacementPageState extends State<PlacementPage> with AutomaticKeepAliveClientMixin {
  int? _locationId;
  final _code = TextEditingController();

  @override
  bool get wantKeepAlive => true;

  Future<void> _send([String? scannedCode]) async {
    final locationId = requireSelected(_locationId, 'Выберите кабинет или склад');
    final code = scannedCode ?? _code.text;
    await widget.onAction(() => widget.api.post('/api/warehouse/placement', {
          'buildingId': widget.buildingId,
          'locationId': locationId,
          'code': code,
        }));
    _code.clear();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final selectedLocation = nameById(widget.locations, _locationId);
    return SectionCard(
      title: 'Размещение',
      children: [
        LocationDropdown(
          locations: widget.locations,
          value: _locationId,
          onChanged: (value) => setState(() => _locationId = value),
        ),
        StatusLine(
          icon: Icons.push_pin,
          title: selectedLocation ?? 'Место не выбрано',
          subtitle: 'Выбранный кабинет сохраняется после сканирования.',
        ),
        CodeInput(
          controller: _code,
          onScan: (code) => _code.text = code,
          onScannedCode: _send,
        ),
        FilledButton.icon(onPressed: () => _send(), icon: const Icon(Icons.meeting_room), label: const Text('Разместить')),
      ],
    );
  }
}

class InventoryPage extends StatefulWidget {
  const InventoryPage({
    super.key,
    required this.api,
    required this.buildingId,
    required this.locations,
    required this.state,
    required this.onAction,
  });

  final ApiClient api;
  final int? buildingId;
  final List<dynamic> locations;
  final Map<String, dynamic>? state;
  final Future<void> Function(Future<void> Function()) onAction;

  @override
  State<InventoryPage> createState() => _InventoryPageState();
}

class _InventoryPageState extends State<InventoryPage> with AutomaticKeepAliveClientMixin {
  int? _locationId;
  int _view = 0;

  @override
  bool get wantKeepAlive => true;

  Future<void> _start() {
    final locationId = requireSelected(_locationId, 'Выберите кабинет или склад');
    return widget.onAction(() => widget.api.post('/api/warehouse/inventory/start', {
          'buildingId': widget.buildingId,
          'locationId': locationId,
        }));
  }

  Future<void> _scan(String code) {
    return widget.onAction(() => widget.api.post('/api/warehouse/inventory/scan', {
          'buildingId': widget.buildingId,
          'code': code,
        }));
  }

  Future<void> _finish() => widget.onAction(() => widget.api.post('/api/warehouse/inventory/finish', {}));

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final state = widget.state;
    final session = state?['session'];
    final location = state?['location'];
    final matched = (state?['matched'] as List<dynamic>?) ?? [];
    final missing = (state?['missing'] as List<dynamic>?) ?? [];
    final surplus = (state?['surplus'] as List<dynamic>?) ?? [];
    final lists = [matched, missing, surplus];
    final titles = ['Найдено', 'Осталось', 'Излишки'];

    return SectionCard(
      title: 'Инвентаризация',
      children: [
        if (session == null) ...[
          LocationDropdown(
            locations: widget.locations,
            value: _locationId,
            onChanged: (value) => setState(() => _locationId = value),
          ),
          FilledButton.icon(onPressed: _start, icon: const Icon(Icons.play_arrow), label: const Text('Начать сессию')),
        ] else ...[
          StatusLine(
            icon: Icons.fact_check,
            title: 'Сессия #${session['id']} · ${location?['name'] ?? ''}',
            subtitle: 'Сканов: ${session['scannedCount']}',
          ),
          InlineScanner(onCode: _scan, height: 330),
          SegmentedButton<int>(
            segments: [
              ButtonSegment(value: 0, label: Text('Найдено ${matched.length}')),
              ButtonSegment(value: 1, label: Text('Осталось ${missing.length}')),
              ButtonSegment(value: 2, label: Text('Излишки ${surplus.length}')),
            ],
            selected: {_view},
            onSelectionChanged: (value) => setState(() => _view = value.first),
          ),
          InventoryList(title: titles[_view], items: lists[_view], surplus: _view == 2),
          OutlinedButton.icon(onPressed: _finish, icon: const Icon(Icons.stop), label: const Text('Завершить сессию')),
        ],
      ],
    );
  }
}

class DisposalPage extends StatefulWidget {
  const DisposalPage({
    super.key,
    required this.api,
    required this.buildingId,
    required this.dueItems,
    required this.state,
    required this.onAction,
  });

  final ApiClient api;
  final int? buildingId;
  final List<dynamic> dueItems;
  final Map<String, dynamic>? state;
  final Future<void> Function(Future<void> Function()) onAction;

  @override
  State<DisposalPage> createState() => _DisposalPageState();
}

class _DisposalPageState extends State<DisposalPage> {
  final _code = TextEditingController();
  final _seal = TextEditingController();

  Future<void> _start() => widget.onAction(() => widget.api.post('/api/warehouse/disposal/start', {'buildingId': widget.buildingId}));

  Future<void> _scan([String? scannedCode]) async {
    final code = scannedCode ?? _code.text;
    await widget.onAction(() => widget.api.post('/api/warehouse/disposal/scan', {
          'buildingId': widget.buildingId,
          'code': code,
        }));
    _code.clear();
  }

  Future<void> _finish() async {
    await widget.onAction(() => widget.api.post('/api/warehouse/disposal/finish', {
          'buildingId': widget.buildingId,
          'sealNumber': _seal.text,
        }));
    _seal.clear();
  }

  @override
  Widget build(BuildContext context) {
    final session = widget.state?['session'];
    final scans = (widget.state?['scans'] as List<dynamic>?) ?? [];
    return SectionCard(
      title: 'Утилизация',
      children: [
        Text('Кандидаты на утилизацию: ${widget.dueItems.length}'),
        if (session == null)
          FilledButton.icon(onPressed: _start, icon: const Icon(Icons.play_arrow), label: const Text('Начать сессию'))
        else ...[
          StatusLine(
            icon: Icons.delete_sweep,
            title: 'Сессия #${session['id']}',
            subtitle: 'Отсканировано: ${session['scannedCount']}',
          ),
          CodeInput(
            controller: _code,
            onScan: (code) => _code.text = code,
            onScannedCode: _scan,
          ),
          FilledButton.icon(onPressed: () => _scan(), icon: const Icon(Icons.qr_code), label: const Text('Добавить в сессию')),
          AppTextField(controller: _seal, label: 'Номер пломбы'),
          OutlinedButton.icon(onPressed: _finish, icon: const Icon(Icons.stop), label: const Text('Завершить и сформировать акт')),
          InventoryList(title: 'В текущей сессии', items: scans),
        ],
        const Divider(),
        ...widget.dueItems.take(8).map((item) => InfoTile(
              title: '${item['inventoryNumber']} · ${item['name']}',
              subtitle: item['locationName'] as String,
            )),
      ],
    );
  }
}

class CodeInput extends StatelessWidget {
  const CodeInput({
    super.key,
    required this.controller,
    required this.onScan,
    this.onScannedCode,
  });

  final TextEditingController controller;
  final ValueChanged<String> onScan;
  final Future<void> Function(String code)? onScannedCode;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: TextField(
            controller: controller,
            decoration: const InputDecoration(labelText: 'Инвентарный номер / ШК'),
            onSubmitted: onScannedCode,
          ),
        ),
        IconButton.filledTonal(
          onPressed: () async {
            final code = await Navigator.of(context).push<String>(
              MaterialPageRoute(builder: (_) => const ScannerScreen()),
            );
            if (code != null) {
              onScan(code);
              await onScannedCode?.call(code);
            }
          },
          icon: const Icon(Icons.qr_code_scanner),
        ),
      ],
    );
  }
}

class InlineScanner extends StatefulWidget {
  const InlineScanner({super.key, required this.onCode, this.height = 320});

  final Future<void> Function(String code) onCode;
  final double height;

  @override
  State<InlineScanner> createState() => _InlineScannerState();
}

class _InlineScannerState extends State<InlineScanner> {
  late final MobileScannerController _controller;
  bool _busy = false;
  String? _lastCode;
  DateTime _lastAt = DateTime.fromMillisecondsSinceEpoch(0);

  @override
  void initState() {
    super.initState();
    _controller = MobileScannerController(
      detectionSpeed: DetectionSpeed.noDuplicates,
      facing: CameraFacing.back,
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _handleDetect(BarcodeCapture capture) async {
    if (_busy) {
      return;
    }
    final code = firstCode(capture);
    if (code == null) {
      return;
    }
    final now = DateTime.now();
    if (_lastCode == code && now.difference(_lastAt).inSeconds < 2) {
      return;
    }
    _busy = true;
    _lastCode = code;
    _lastAt = now;
    try {
      await widget.onCode(code);
    } finally {
      await Future<void>.delayed(const Duration(milliseconds: 500));
      _busy = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        height: widget.height,
        child: Stack(
          fit: StackFit.expand,
          children: [
            MobileScanner(
              controller: _controller,
              fit: BoxFit.cover,
              onDetect: _handleDetect,
              errorBuilder: (context, error, child) => ScannerError(message: error.toString()),
            ),
            IgnorePointer(
              child: Center(
                child: Container(
                  width: 250,
                  height: 150,
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.white, width: 3),
                    borderRadius: BorderRadius.circular(18),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class ScannerScreen extends StatefulWidget {
  const ScannerScreen({super.key});

  @override
  State<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<ScannerScreen> {
  late final MobileScannerController _controller;
  bool _handled = false;

  @override
  void initState() {
    super.initState();
    _controller = MobileScannerController(
      detectionSpeed: DetectionSpeed.noDuplicates,
      facing: CameraFacing.back,
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _handleDetect(BarcodeCapture capture) {
    if (_handled) {
      return;
    }
    final code = firstCode(capture);
    if (code == null) {
      return;
    }
    _handled = true;
    _controller.stop();
    Navigator.of(context).pop(code);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Сканирование')),
      body: Stack(
        fit: StackFit.expand,
        children: [
          MobileScanner(
            controller: _controller,
            fit: BoxFit.cover,
            onDetect: _handleDetect,
            errorBuilder: (context, error, child) => ScannerError(message: error.toString()),
          ),
          IgnorePointer(
            child: Center(
              child: Container(
                width: 260,
                height: 180,
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.white, width: 3),
                  borderRadius: BorderRadius.circular(24),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class ScannerError extends StatelessWidget {
  const ScannerError({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: const Color(0xFF0E2A47),
      padding: const EdgeInsets.all(20),
      child: Center(
        child: Text(
          'Камера не запустилась.\n\n$message\n\nПроверьте разрешение камеры для приложения.',
          textAlign: TextAlign.center,
          style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w700),
        ),
      ),
    );
  }
}

class InventoryList extends StatelessWidget {
  const InventoryList({super.key, required this.title, required this.items, this.surplus = false});

  final String title;
  final List<dynamic> items;
  final bool surplus;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return EmptyText('$title: нет данных');
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 8),
        ...items.map((item) {
          final map = item as Map<String, dynamic>;
          final inventoryNumber = map['inventoryNumber'] ?? '';
          final name = map['name'] ?? '';
          final type = map['type'] ?? '-';
          final location = map['locationName'] ?? '-';
          final assignedTo = map['assignedTo'] ?? '-';
          return InfoTile(
            title: '$inventoryNumber · $name',
            subtitle: surplus ? '$type · числится: $location' : '$type · $location · $assignedTo',
          );
        }),
      ],
    );
  }
}

class BuildingSelector extends StatelessWidget {
  const BuildingSelector({
    super.key,
    required this.buildings,
    required this.value,
    required this.onChanged,
  });

  final List<dynamic> buildings;
  final int? value;
  final ValueChanged<int?> onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<int>(
      value: value,
      decoration: const InputDecoration(labelText: 'Корпус'),
      items: buildings
          .map((building) => DropdownMenuItem<int>(
                value: building['id'] as int,
                child: Text(building['name'] as String),
              ))
          .toList(),
      onChanged: onChanged,
    );
  }
}

class LocationDropdown extends StatelessWidget {
  const LocationDropdown({
    super.key,
    required this.locations,
    required this.value,
    required this.onChanged,
  });

  final List<dynamic> locations;
  final int? value;
  final ValueChanged<int?> onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<int>(
      value: value,
      decoration: const InputDecoration(labelText: 'Кабинет или склад'),
      items: locations
          .map((location) => DropdownMenuItem<int>(
                value: location['id'] as int,
                child: Text(location['name'] as String),
              ))
          .toList(),
      onChanged: onChanged,
    );
  }
}

class SectionCard extends StatelessWidget {
  const SectionCard({super.key, required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(title, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w900)),
            const SizedBox(height: 12),
            ...children.map((child) => Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: child,
                )),
          ],
        ),
      ),
    );
  }
}

class StatusLine extends StatelessWidget {
  const StatusLine({super.key, required this.icon, required this.title, required this.subtitle});

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: const Color(0xFFF7FAFF),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Icon(icon),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
                  Text(subtitle),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class InfoTile extends StatelessWidget {
  const InfoTile({super.key, required this.title, required this.subtitle});

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      dense: true,
      tileColor: const Color(0xFFF7FAFF),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      title: Text(title),
      subtitle: Text(subtitle),
    );
  }
}

class EmptyText extends StatelessWidget {
  const EmptyText(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(text, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant));
  }
}

class ErrorBox extends StatelessWidget {
  const ErrorBox(this.message, {super.key});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF0F1),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Text(message, style: const TextStyle(color: Color(0xFFD33F49))),
    );
  }
}

class AppTextField extends StatelessWidget {
  const AppTextField({
    super.key,
    required this.controller,
    required this.label,
    this.obscure = false,
  });

  final TextEditingController controller;
  final String label;
  final bool obscure;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextField(
        controller: controller,
        obscureText: obscure,
        decoration: InputDecoration(labelText: label),
      ),
    );
  }
}

class ApiClient {
  ApiClient({
    required String baseUrl,
    required this.username,
    required this.password,
  }) : baseUrl = baseUrl.replaceAll(RegExp(r'/$'), '');

  final String baseUrl;
  final String username;
  final String password;

  Map<String, String> get _headers => {
        'Authorization': 'Basic ${base64Encode(utf8.encode('$username:$password'))}',
        'Content-Type': 'application/json',
      };

  Future<Map<String, dynamic>> get(String path) async {
    final response = await http.get(Uri.parse('$baseUrl$path'), headers: _headers);
    return _decode(response) as Map<String, dynamic>;
  }

  Future<List<dynamic>> getList(String path) async {
    final response = await http.get(Uri.parse('$baseUrl$path'), headers: _headers);
    return _decode(response) as List<dynamic>;
  }

  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> body) async {
    final response = await http.post(
      Uri.parse('$baseUrl$path'),
      headers: _headers,
      body: jsonEncode(body),
    );
    return _decode(response) as Map<String, dynamic>;
  }

  Object _decode(http.Response response) {
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception(response.body.isEmpty ? 'HTTP ${response.statusCode}' : utf8.decode(response.bodyBytes));
    }
    if (response.body.isEmpty) {
      return <String, dynamic>{};
    }
    return jsonDecode(utf8.decode(response.bodyBytes));
  }
}

String? firstCode(BarcodeCapture capture) {
  for (final barcode in capture.barcodes) {
    final value = barcode.rawValue?.trim();
    if (value != null && value.isNotEmpty) {
      return value;
    }
  }
  return null;
}

int requireSelected(int? value, String message) {
  if (value == null) {
    throw Exception(message);
  }
  return value;
}

String? nameById(List<dynamic> values, int? id) {
  if (id == null) {
    return null;
  }
  for (final value in values) {
    if (value['id'] == id) {
      return value['name'] as String?;
    }
  }
  return null;
}

String cleanupError(Object error) {
  return error.toString().replaceFirst('Exception: ', '');
}
