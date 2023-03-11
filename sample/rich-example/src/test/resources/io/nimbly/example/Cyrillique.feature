# language: ru
@Web @Electron
@Dev @Production
Функция: Сxервисные сценар
  Создание аккаунтов, площадок и т.д. для настройки окружени
###########################################################
  à@Off
  Сценарий: Генерация тестовых аккаунтов вместе с площадками
#    Когда система помещает в переменную "${файл}" значение "csv_data/accounts_developer1/Testing_Accounts.csv"
#    Когда система помещает в переменную "${файл}" значение "csv_data/accounts_developer1/Staging_Accounts.csv"
#   Когда система помещает в переменную "${файл}" значение "csv_data/accounts_LinuxVM/Testing_Accounts.csv"
#    Когда система помещает в переменную "${файл}" значение "csv_data/accounts_LinuxVM/Staging_Accounts.csv"
    Тогда система создает 10 тестовых аккаунтов и сохраняет их в файл "${файл}"
    И система создает площадки и вставляет виджет для тестовых аккаунтов из файла "${файл}"

###########################################################
  @Off
  Сценарий: Генерация тестовых аккаунтов без сайта (площадки тоже не делаем)
#    Когда система помещает в переменную "${файл}" значение "csv_data/accounts_developer1/Testing_Accounts_Without_Sites.csv"
#    Когда система помещает в переменную "${файл}" значение "csv_data/accounts_developer1/Staging_Accounts_Without_Sites.csv"
#    Когда система помещает в переменную "${файл}" значение "csv_data/accounts_LinuxVM/Testing_Accounts_Without_Sites.csv"
   Когда система помещает в переменную "${файл}" значение "csv_data/accounts_LinuxVM/Staging_Accounts_Without_Sites.csv"
    Тогда система создает 10 тестовых аккаунтов без сайта и сохраняет их в файл "${файл}"


##########################################################
  @Off
  Сценарий: Генерация тестовых аккаунтов с забытым паролем вместе с площадками
#    Когда система помещает в переменную "${файл}" значение "csv_data/accounts_developer1/Testing_Accounts_Forgotten_Password.csv"
#    Когда система помещает в переменную "${файл}" значение "csv_data/accounts_developer1/Staging_Accounts_Forgotten_Password.csv"
#   Когда система помещает в переменную "${файл}" значение "csv_data/accounts_LinuxVM/Testing_Accounts_Forgotten_Password.csv"
    Когда система помещает в переменную "${файл}" значение "csv_data/accounts_LinuxVM/Staging_Accounts_Forgotten_Password.csv"
    Тогда система создает 10 тестовых аккаунтов и сохраняет их в файл "${файл}"
    И система создает площадки и вставляет виджет для тестовых аккаунтов из файла "${файл}"
