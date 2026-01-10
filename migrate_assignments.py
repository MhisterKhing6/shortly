#!/usr/bin/env python3
"""
Migration script to convert old assignment data to the new format.
Groups assignments by assignedAt timestamp and officeId, matching riders via phone numbers.
"""

import json
from collections import defaultdict
from typing import Dict, List, Any, Optional

def load_json_file(file_path: str) -> List[Dict]:
    """Load JSON data from a file."""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        print(f"Error loading {file_path}: {e}")
        return []

def save_json_file(file_path: str, data: List[Dict]):
    """Save JSON data to a file."""
    try:
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"Successfully saved {len(data)} assignments to {file_path}")
    except Exception as e:
        print(f"Error saving {file_path}: {e}")

def create_rider_lookup(users: List[Dict]) -> Dict[str, Dict]:
    """Create a lookup dictionary mapping phone numbers to rider information."""
    rider_lookup = {}
    for user in users:
        if user.get('role') == 'RIDER':
            phone = user.get('phoneNumber', '').strip()
            if phone:
                rider_lookup[phone] = {
                    'riderId': user.get('userId'),
                    'riderName': user.get('name'),
                    'riderPhoneNumber': phone
                }
    return rider_lookup

def create_parcel_lookup(parcels: List[Dict]) -> Dict[str, Dict]:
    """Create a lookup dictionary mapping parcelId to parcel information."""
    parcel_lookup = {}
    for parcel in parcels:
        parcel_id = parcel.get('parcelId')
        if parcel_id:
            parcel_lookup[parcel_id] = parcel
    return parcel_lookup

def find_rider_by_driver_phone(driver_phone: str, rider_lookup: Dict[str, Dict], office_id: str) -> Optional[Dict]:
    """
    Find a rider by matching the driver phone number.
    Handles phone number variations and formatting.
    """
    if not driver_phone:
        return None

    # Clean the phone number
    clean_phone = driver_phone.strip()

    # Direct match
    if clean_phone in rider_lookup:
        return rider_lookup[clean_phone]

    # Try without country code variations
    for rider_phone, rider_info in rider_lookup.items():
        if clean_phone in rider_phone or rider_phone in clean_phone:
            return rider_info

    return None

def create_driver_to_rider_mapping(parcels: List[Dict], users: List[Dict]) -> Dict[str, Dict]:
    """
    Create a mapping from driver phone numbers (in parcels) to actual riders (in users).
    Uses office_id and frequency analysis to make best guesses.
    """
    from collections import defaultdict, Counter

    # Get all riders by office
    riders_by_office = defaultdict(list)
    for user in users:
        if user.get('role') == 'RIDER':
            office = user.get('officeId')
            riders_by_office[office].append({
                'riderId': user.get('userId'),
                'riderName': user.get('name'),
                'riderPhoneNumber': user.get('phoneNumber')
            })

    # Count driver phone usage by office
    driver_office_counts = defaultdict(lambda: defaultdict(int))
    for parcel in parcels:
        if parcel.get('parcelAssigned'):
            driver_phone = parcel.get('driverPhoneNumber')
            office_id = parcel.get('officeId')
            if driver_phone and office_id:
                driver_office_counts[driver_phone][office_id] += 1

    # Create mapping
    mapping = {}
    for driver_phone, office_counts in driver_office_counts.items():
        # Get the office where this driver is most active
        primary_office = max(office_counts.items(), key=lambda x: x[1])[0]

        # Get riders for that office
        riders = riders_by_office.get(primary_office, [])

        if riders:
            # Assign the first available rider for that office
            # In a real scenario, you might want to distribute more evenly
            mapping[driver_phone] = riders[0]

    return mapping

def get_parcels_for_assignment(assignment: Dict, all_parcels: List[Dict], parcel_lookup: Dict) -> List[Dict]:
    """
    Find all parcels that belong to this assignment.
    Matches based on officeId, assignedAt timestamp, and assignment status.
    """
    assigned_at = assignment.get('assignedAt')
    office_id = assignment.get('officeId')
    assignment_status = assignment.get('status')

    # Determine if this assignment represents delivered parcels
    is_delivered_assignment = assignment_status in ['DELIVERED', 'COMPLETED']

    matching_parcels = []

    for parcel in all_parcels:
        # Skip parcels that are already delivered unless this is a completed assignment
        if parcel.get('delivered') and not is_delivered_assignment:
            continue

        # Must be assigned
        if not parcel.get('parcelAssigned'):
            continue

        # Match office ID
        parcel_office = parcel.get('officeId')
        if office_id and parcel_office != office_id:
            continue

        # For assigned parcels, we include them if they match the criteria
        # The timestamp matching is fuzzy since we don't have exact timestamps on parcels
        matching_parcels.append(parcel)

    return matching_parcels

def create_parcel_info(parcel: Dict) -> Dict:
    """Create a ParcelInfo object from parcel data."""
    return {
        'parcelId': parcel.get('parcelId'),
        'parcelDescription': parcel.get('parcelDescription', ''),
        'receiverName': parcel.get('receiverName', ''),
        'receiverPhoneNumber': parcel.get('recieverPhoneNumber', ''),  # Note: typo in source
        'receiverAddress': parcel.get('receiverAddress', ''),
        'senderName': parcel.get('senderName', ''),
        'senderPhoneNumber': parcel.get('senderPhoneNumber', ''),
        'parcelAmount': parcel.get('deliveryCost', 0.0),
        'payed': False,
        'cancelled': False
    }

def group_assignments_by_key(assignments: List[Dict]) -> Dict[tuple, List[Dict]]:
    """Group assignments by (assignedAt, officeId) key."""
    grouped = defaultdict(list)
    for assignment in assignments:
        assigned_at = assignment.get('assignedAt')
        office_id = assignment.get('officeId', '')
        key = (assigned_at, office_id)
        grouped[key].append(assignment)
    return grouped

def migrate_assignments(assignments: List[Dict], parcels: List[Dict], users: List[Dict]) -> List[Dict]:
    """
    Main migration logic.
    Groups assignments and creates new consolidated assignment objects.
    """
    rider_lookup = create_rider_lookup(users)
    parcel_lookup = create_parcel_lookup(parcels)
    driver_to_rider_mapping = create_driver_to_rider_mapping(parcels, users)

    print(f"Found {len(rider_lookup)} riders in users data")
    print(f"Found {len(parcel_lookup)} parcels")
    print(f"Created {len(driver_to_rider_mapping)} driver-to-rider mappings")
    print(f"Processing {len(assignments)} assignments")

    # Print the mapping for verification
    print("\nDriver to Rider Mapping:")
    for driver_phone, rider_info in driver_to_rider_mapping.items():
        print(f"  {driver_phone} -> {rider_info['riderName']} ({rider_info['riderPhoneNumber']})")

    # Group assignments by timestamp and office
    grouped_assignments = group_assignments_by_key(assignments)
    print(f"\nGrouped into {len(grouped_assignments)} assignment groups")

    migrated_assignments = []
    processed_parcels = set()

    # Process each group
    for (assigned_at, office_id), assignment_group in grouped_assignments.items():
        print(f"\nProcessing group: assignedAt={assigned_at}, officeId={office_id}, count={len(assignment_group)}")

        # Find parcels for this group
        # For simplicity, we'll use parcels with matching office and assignment status
        group_parcels = []
        rider_phone = None

        # Try to find the rider from parcels' driver phone numbers
        for assignment in assignment_group:
            # Get parcels that were part of this assignment
            potential_parcels = [p for p in parcels
                                if p.get('officeId') == office_id
                                and p.get('parcelAssigned')
                                and p.get('parcelId') not in processed_parcels]

            # For assignments that share the same timestamp, find common driver phone
            for parcel in potential_parcels:
                driver_phone = parcel.get('driverPhoneNumber')
                if driver_phone and not rider_phone:
                    rider_phone = driver_phone

                # Include parcels that match the criteria
                if parcel.get('parcelId') not in processed_parcels:
                    # Check if this parcel should be included
                    delivered = parcel.get('delivered', False)
                    assignment_status = assignment.get('status', '')

                    # Include undelivered parcels for pending/assigned assignments
                    # Include delivered parcels for completed/delivered assignments
                    if (not delivered and assignment_status in ['PENDING', 'ASSIGNED', 'ACCEPTED']) or \
                       (delivered and assignment_status in ['DELIVERED', 'COMPLETED']):
                        if len(group_parcels) < len(assignment_group):  # Limit to reasonable number
                            group_parcels.append(parcel)
                            processed_parcels.add(parcel.get('parcelId'))

        # Find the rider using the mapping
        rider_info = None
        if rider_phone and rider_phone in driver_to_rider_mapping:
            rider_info = driver_to_rider_mapping[rider_phone]
        elif rider_phone:
            # Try direct lookup
            rider_info = find_rider_by_driver_phone(rider_phone, rider_lookup, office_id)

        # If no rider found, try to assign based on office
        if not rider_info and office_id:
            # Find any rider for this office
            for user in users:
                if user.get('role') == 'RIDER' and user.get('officeId') == office_id:
                    rider_info = {
                        'riderId': user.get('userId'),
                        'riderName': user.get('name'),
                        'riderPhoneNumber': user.get('phoneNumber')
                    }
                    break

        # If still no rider found, use fallback
        if not rider_info:
            print(f"  Warning: No rider found for group (driver phone: {rider_phone}, office: {office_id})")
            # Try to use any available rider as fallback
            if rider_lookup:
                rider_info = list(rider_lookup.values())[0]
                print(f"  Using fallback rider: {rider_info.get('riderName')}")
            else:
                print(f"  Skipping group - no riders available")
                continue
        else:
            print(f"  Assigned to rider: {rider_info.get('riderName')}")

        # If no parcels found, try a different approach
        if not group_parcels:
            print(f"  Warning: No parcels found for this group")
            # Try to find any unassigned parcels for this office
            unassigned = [p for p in parcels
                         if p.get('officeId') == office_id
                         and not p.get('delivered')
                         and p.get('parcelId') not in processed_parcels]

            if unassigned and len(unassigned) <= 5:  # Take a small batch
                group_parcels = unassigned[:len(assignment_group)]
                for p in group_parcels:
                    processed_parcels.add(p.get('parcelId'))

        # Create parcel info objects
        parcel_infos = [create_parcel_info(p) for p in group_parcels]

        # Calculate total amount
        total_amount = sum(p.get('deliveryCost', 0.0) for p in group_parcels)

        # Get representative assignment for metadata
        representative = assignment_group[0]

        # Create the new consolidated assignment
        new_assignment = {
            'assignmentId': representative.get('assignmentId'),
            'riderInfo': rider_info,
            'parcels': parcel_infos,
            'amount': total_amount,
            'status': representative.get('status'),
            'officeId': office_id,
            'assignedAt': assigned_at,
            'acceptedAt': representative.get('acceptedAt', 0),
            'completedAt': representative.get('completedAt', 0),
            'confirmationCode': representative.get('confirmationCode'),
            'payed': representative.get('payed', False),
            'payementMethod': representative.get('payementMethod'),
            'cancelationReason': representative.get('cancelationReason'),
            'createdAt': representative.get('createdAt'),
            'updatedAt': representative.get('updatedAt')
        }

        migrated_assignments.append(new_assignment)
        print(f"  Created assignment with {len(parcel_infos)} parcels, total: {total_amount}")

    print(f"\nMigration complete: {len(migrated_assignments)} assignments created")
    return migrated_assignments

def main():
    """Main execution function."""
    # File paths
    assignments_file = '/home/kingsley-botchway/Desktop/shortlyapplication/shortly/assignment.txt'
    parcels_file = '/home/kingsley-botchway/Desktop/shortlyapplication/shortly/parcels.txt'
    users_file = '/home/kingsley-botchway/Desktop/shortlyapplication/shortly/users.txt'
    output_file = '/home/kingsley-botchway/Desktop/shortlyapplication/shortly/migrated_assignments_final.json'

    print("Loading data files...")
    assignments = load_json_file(assignments_file)
    parcels = load_json_file(parcels_file)
    users = load_json_file(users_file)

    if not assignments or not parcels or not users:
        print("Error: Could not load all required data files")
        return

    print(f"Loaded {len(assignments)} assignments")
    print(f"Loaded {len(parcels)} parcels")
    print(f"Loaded {len(users)} users")

    # Perform migration
    migrated = migrate_assignments(assignments, parcels, users)

    # Save results
    save_json_file(output_file, migrated)

    # Print statistics
    total_parcels = sum(len(a.get('parcels', [])) for a in migrated)
    total_amount = sum(a.get('amount', 0) for a in migrated)

    print(f"\nMigration Statistics:")
    print(f"  Total assignments: {len(migrated)}")
    print(f"  Total parcels: {total_parcels}")
    print(f"  Total amount: {total_amount}")
    print(f"\nOutput saved to: {output_file}")

if __name__ == '__main__':
    main()
