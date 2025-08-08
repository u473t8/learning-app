#!/usr/bin/bash

export BORG_REPO=ssh://storagebox/./borg-repository

export BORG_PASSPHRASE=$(cat $BORG_PASSPHRASE_PATH)

### Remove current env when db moved to the STATE_DIRECTORY location
export LEARNING_APP_DB_PATH=/opt/learning-app/app.db


# some helpers and error handling:
info() { printf "\n%s %s\n\n" "$( date )" "$*" >&2; }

trap 'echo $( date ) Backup interrupted >&2; exit 2' INT TERM


info "Starting backup"

# Create local backup
sqlite3 ${LEARNING_APP_DB_PATH} ".backup $LEARNING_APP_DB_BACKUP_PATH"
info "Check backup"
sqlite3 ${LEARNING_APP_DB_BACKUP_PATH} ".selftest"

sqlite_backup_exit=$?

# Create remote backup
borg create                     \
    --list                      \
    --stats                     \
    --show-rc                   \
    ::db-{now}.sqlite           \
    $LEARNING_APP_DB_BACKUP_PATH

borg_backup_exit=$?


info "Pruning repository"

# Use the `prune` subcommand to maintain 7 daily, 4 weekly and 6 monthly archives.

borg prune            \
    --list            \
    --show-rc         \
    --keep-daily    7 \
    --keep-weekly   4 \
    --keep-monthly  6

prune_exit=$?


# actually free repo disk space by compacting segments

info "Compacting repository"

borg compact

compact_exit=$?

# use highest exit code as global exit code
global_exit=$(( borg_backup_exit > sqlite_backup_exit ? borg_backup_exit : sqlite_backup_exit ))
global_exit=$(( prune_exit > global_exit ? prune_exit : global_exit ))
global_exit=$(( compact_exit > global_exit ? compact_exit : global_exit ))

if [ ${global_exit} -eq 0 ]; then
    info "Backup, Prune, and Compact finished successfully"
elif [ ${global_exit} -eq 1 ]; then
    info "Backup, Prune, and/or Compact finished with warnings"
else
    info "Backup, Prune, and/or Compact finished with errors"
fi

exit ${global_exit}
